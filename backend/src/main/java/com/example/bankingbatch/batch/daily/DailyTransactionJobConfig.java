package com.example.bankingbatch.batch.daily;

import com.example.bankingbatch.batch.exception.InsufficientFundsException;
import com.example.bankingbatch.batch.metrics.BatchMetricsListener;
import com.example.bankingbatch.batch.metrics.StepTimingListener;
import com.example.bankingbatch.domain.Account;
import com.example.bankingbatch.domain.AuditLog;
import com.example.bankingbatch.domain.TransactionStaging;
import com.example.bankingbatch.repository.AccountRepository;
import com.example.bankingbatch.repository.AuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.StepListenerSupport;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.jdbc.core.RowMapper;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

// Configures the Daily Transaction Processing job: reads unprocessed staging rows,
// applies credit/debit to accounts, writes audit logs, and marks records processed.
// Handles errors gracefully: logs failures and continues processing remaining transactions.
@Configuration
public class DailyTransactionJobConfig {

    private static final Logger logger = LoggerFactory.getLogger(DailyTransactionJobConfig.class);
    
    private final EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public DailyTransactionJobConfig(EntityManagerFactory entityManagerFactory,
                                     DataSource dataSource,
                                     AccountRepository accountRepository,
                                     AuditLogRepository auditLogRepository,
                                     MeterRegistry meterRegistry,
                                     JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager) {
        this.entityManagerFactory = entityManagerFactory;
        this.dataSource = dataSource;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.meterRegistry = meterRegistry;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    /**
     * Listener bean that logs step-level timings for this job.
     */
    @Bean
    public StepTimingListener dailyStepTimingListener() {
        return new StepTimingListener();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<TransactionStaging> dailyTransactionReader() throws Exception {
        RowMapper<TransactionStaging> rowMapper = new RowMapper<TransactionStaging>() {
            @Override
            public TransactionStaging mapRow(ResultSet rs, int rowNum) throws SQLException {
                return TransactionStaging.builder()
                        .id(rs.getLong("id"))
                        .txnId(rs.getString("txn_id"))
                        .accountNumber(rs.getString("account_number"))
                        .amount(rs.getBigDecimal("amount"))
                        .direction(rs.getString("direction"))
                        .processedFlag(rs.getBoolean("processed_flag"))
                        .createdAt(rs.getTimestamp("created_at") != null ? 
                                rs.getTimestamp("created_at").toLocalDateTime() : LocalDateTime.now())
                        .build();
            }
        };
        
        SqlPagingQueryProviderFactoryBean queryProvider = new SqlPagingQueryProviderFactoryBean();
        queryProvider.setDataSource(dataSource);
        queryProvider.setSelectClause("SELECT id, txn_id, account_number, amount, direction, processed_flag, created_at");
        queryProvider.setFromClause("FROM transaction_staging");
        queryProvider.setWhereClause("WHERE processed_flag = false");
        queryProvider.setSortKey("id");
        
        return new JdbcPagingItemReaderBuilder<TransactionStaging>()
                .name("dailyTransactionReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider.getObject())
                .pageSize(100)
                .rowMapper(rowMapper)
                .build();
    }

    @Bean
    public ItemProcessor<TransactionStaging, TransactionStaging> dailyTransactionProcessor() {
        return item -> {
            try {
                // Load account and apply transaction with HALF_EVEN rounding.
                Account account = accountRepository.findByAccountNumber(item.getAccountNumber())
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + item.getAccountNumber()));

                BigDecimal amount = item.getAmount().setScale(2, RoundingMode.HALF_EVEN);
                BigDecimal newBalance;

                if ("CREDIT".equalsIgnoreCase(item.getDirection())) {
                    newBalance = account.getBalance().add(amount);
                } else if ("DEBIT".equalsIgnoreCase(item.getDirection())) {
                    newBalance = account.getBalance().subtract(amount);
                } else {
                    throw new IllegalArgumentException("Unknown direction: " + item.getDirection());
                }

                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new InsufficientFundsException("Insufficient funds for account " + account.getAccountNumber());
                }

                account.setBalance(newBalance.setScale(2, RoundingMode.HALF_EVEN));
                accountRepository.save(account);

                // Persist lightweight audit entry; payload kept simple JSON string.
                AuditLog log = AuditLog.builder()
                        .eventType("DAILY_TXN")
                        .reference(item.getTxnId())
                        .payload("{\"accountNumber\":\"" + account.getAccountNumber() + "\",\"amount\":" + amount + ",\"direction\":\"" + item.getDirection() + "\"}")
                        .createdAt(LocalDateTime.now())
                        .build();
                auditLogRepository.save(log);

                // Mark processed
                item.setProcessedFlag(true);
                return item;
            } catch (InsufficientFundsException | IllegalArgumentException e) {
                // Log failure but don't throw; return null to skip writing
                logger.warn("Failed to process transaction ID {}: {}", item.getTxnId(), e.getMessage());
                item.setProcessedFlag(false); // Mark as not processed so it can be retried
                return null; // Returning null filters this item from the writer
            }
        };
    }

    @Bean
    @StepScope
    public JpaItemWriter<TransactionStaging> dailyTransactionWriter() {
        // Writer persists the processedFlag changes to TransactionStaging entities.
        // The processor already updates Account and AuditLog, and sets processedFlag=true.
        // JpaItemWriter handles EntityManager properly and ensures updates are visible to subsequent reads.
        return new JpaItemWriterBuilder<TransactionStaging>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    /**
     * Listener bean that tracks failed transactions and logs summary statistics.
     */
    @Bean
    public TransactionFailureListener dailyTransactionFailureListener() {
        return new TransactionFailureListener();
    }

    @Bean
    public Step dailyTransactionStep(JdbcPagingItemReader<TransactionStaging> dailyTransactionReader,
                                     @Qualifier("dailyStepTimingListener") StepTimingListener dailyStepTimingListener,
                                     @Qualifier("dailyTransactionFailureListener") TransactionFailureListener failureListener) {
        return new StepBuilder("dailyTransactionStep", jobRepository)
                .<TransactionStaging, TransactionStaging>chunk(100, transactionManager)
                .reader(dailyTransactionReader)
                .processor(dailyTransactionProcessor())
                .writer(dailyTransactionWriter())
                .listener((org.springframework.batch.core.StepExecutionListener) dailyStepTimingListener)
                .listener((org.springframework.batch.core.StepExecutionListener) failureListener)
                .build();
    }

    @Bean

    public Job dailyTransactionJob(@Qualifier("dailyTransactionStep") Step dailyTransactionStep) {
        return new JobBuilder("dailyTransactionJob", jobRepository)
                .start(dailyTransactionStep)
                .listener(new BatchMetricsListener(meterRegistry))
                .build();
    }
}

/**
 * Listener that tracks and logs the count of failed transactions in the daily job.
 */
class TransactionFailureListener extends StepListenerSupport<TransactionStaging, TransactionStaging> {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionFailureListener.class);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    
    @Override
    public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
        failureCount.set(0);
        logger.info("Daily transaction step starting");
    }
    
    @Override
    public ExitStatus afterStep(org.springframework.batch.core.StepExecution stepExecution) {
        long totalProcessed = stepExecution.getReadCount();
        long writeCount = stepExecution.getWriteCount();
        long failures = totalProcessed - writeCount;
        
        if (failures > 0) {
            logger.warn("Daily transaction step completed with {} failed entries out of {} total. {} successfully processed.", 
                    failures, totalProcessed, writeCount);
        } else {
            logger.info("Daily transaction step completed successfully. Processed {} entries.", writeCount);
        }
        logger.info("Read: {}, Written: {}, Failed: {}", totalProcessed, writeCount, failures);
        
        // Always return normal exit status so the job completes even with failures
        return stepExecution.getExitStatus();
    }
}


