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
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

// Configures the Daily Transaction Processing job: reads unprocessed staging rows,
// applies credit/debit to accounts, writes audit logs, and marks records processed.
@Configuration
public class DailyTransactionJobConfig {

    private final EntityManagerFactory entityManagerFactory;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public DailyTransactionJobConfig(EntityManagerFactory entityManagerFactory,
                                     AccountRepository accountRepository,
                                     AuditLogRepository auditLogRepository,
                                     MeterRegistry meterRegistry,
                                     JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager) {
        this.entityManagerFactory = entityManagerFactory;
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
    public JpaPagingItemReader<TransactionStaging> dailyTransactionReader() {
        return new JpaPagingItemReaderBuilder<TransactionStaging>()
                .name("dailyTransactionReader")
                .entityManagerFactory(entityManagerFactory)
                .pageSize(100)
                .queryString("SELECT t FROM TransactionStaging t WHERE t.processedFlag = false ORDER BY t.id ASC")
                .build();
    }

    @Bean
    public ItemProcessor<TransactionStaging, TransactionStaging> dailyTransactionProcessor() {
        return item -> {
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

            // Optionally mark processed (can also be done in writer)
            item.setProcessedFlag(true);
            return item;
        };
    }

    @Bean
    @Transactional
    public ItemWriter<TransactionStaging> dailyTransactionWriter() {
        // No-op writer because processor already updates Account and AuditLog.
        // For Spring Batch 5, the ItemWriter interface uses Chunk<? extends T>.
        return items -> {
            // Intentionally empty: all work is done in the processor.
        };
    }

    @Bean
    public Step dailyTransactionStep(JpaPagingItemReader<TransactionStaging> dailyTransactionReader,
                                     StepTimingListener dailyStepTimingListener) {
        return new StepBuilder("dailyTransactionStep", jobRepository)
                .<TransactionStaging, TransactionStaging>chunk(100, transactionManager)
                .reader(dailyTransactionReader)
                .processor(dailyTransactionProcessor())
                .writer(dailyTransactionWriter())
                .listener(dailyStepTimingListener)
                .faultTolerant()
                .skip(IllegalArgumentException.class)
                .skip(InsufficientFundsException.class)
                .build();
    }

    @Bean
    public Job dailyTransactionJob(Step dailyTransactionStep) {
        return new JobBuilder("dailyTransactionJob", jobRepository)
                .start(dailyTransactionStep)
                .listener(new BatchMetricsListener(meterRegistry))
                .build();
    }
}


