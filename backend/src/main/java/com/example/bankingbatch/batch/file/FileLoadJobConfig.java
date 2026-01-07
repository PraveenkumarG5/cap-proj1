package com.example.bankingbatch.batch.file;

import com.example.bankingbatch.batch.metrics.StepTimingListener;
import com.example.bankingbatch.domain.JobRunLog;
import com.example.bankingbatch.domain.TransactionStaging;
import com.example.bankingbatch.repository.JobRunLogRepository;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Configures the CSV file-load job that stages raw transactions into transaction_staging.
@Configuration
public class FileLoadJobConfig {

    private final DataSource dataSource;
    private final JobRunLogRepository jobRunLogRepository;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public FileLoadJobConfig(DataSource dataSource,
                             JobRunLogRepository jobRunLogRepository,
                             JobRepository jobRepository,
                             PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.jobRunLogRepository = jobRunLogRepository;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    /**
     * Listener used to log timing information for the file-load step.
     */
    @Bean
    public StepTimingListener fileLoadStepTimingListener() {
        return new StepTimingListener();
    }

    @Bean
    public FlatFileItemReader<TransactionStaging> fileLoadReader(
            @Value("#{jobParameters['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<TransactionStaging>()
                .name("fileLoadReader")
                .resource(new FileSystemResource(filePath))
                .linesToSkip(1)
                .delimited()
                .names("txnId", "accountNumber", "amount", "direction")
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(TransactionStaging.class);
                }})
                .build();
    }

    @Bean
    public ItemProcessor<TransactionStaging, TransactionStaging> fileLoadProcessor() {
        return item -> {
            // Basic validation; malformed rows get skipped by fault tolerance.
            if (item.getTxnId() == null || item.getAccountNumber() == null || item.getDirection() == null) {
                throw new IllegalArgumentException("Missing required CSV columns");
            }
            if (item.getAmount() == null) {
                item.setAmount(BigDecimal.ZERO);
            }
            if (item.getCreatedAt() == null) {
                item.setCreatedAt(LocalDateTime.now());
            }
            item.setProcessedFlag(false);
            return item;
        };
    }

    @Bean
    public JdbcBatchItemWriter<TransactionStaging> fileLoadWriter() {
        return new JdbcBatchItemWriterBuilder<TransactionStaging>()
                .dataSource(dataSource)
                .sql("INSERT INTO transaction_staging (txn_id, account_number, amount, direction, processed_flag, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)")
                .itemPreparedStatementSetter((item, ps) -> {
                    ps.setString(1, item.getTxnId());
                    ps.setString(2, item.getAccountNumber());
                    ps.setBigDecimal(3, item.getAmount());
                    ps.setString(4, item.getDirection());
                    ps.setBoolean(5, item.isProcessedFlag());
                    ps.setObject(6, item.getCreatedAt());
                })
                .build();
    }

    @Bean
    public Step fileLoadStep(FlatFileItemReader<TransactionStaging> fileLoadReader,
                             JdbcBatchItemWriter<TransactionStaging> fileLoadWriter,
                             StepTimingListener fileLoadStepTimingListener) {
        return new StepBuilder("fileLoadStep", jobRepository)
                .<TransactionStaging, TransactionStaging>chunk(1000, transactionManager)
                .reader(fileLoadReader)
                .processor(fileLoadProcessor())
                .writer(fileLoadWriter)
                .listener(fileLoadStepTimingListener)
                .faultTolerant()
                .skip(IllegalArgumentException.class)
                .build();
    }

    @Bean
    public Job fileLoadJob(Step fileLoadStep) {
        return new JobBuilder("fileLoadJob", jobRepository)
                .listener(new JobExecutionListener() {
                    @Override
                    public void beforeJob(JobExecution jobExecution) {
                        // Record start in JobRunLog for simple auditability.
                        JobRunLog log = JobRunLog.builder()
                                .jobName("fileLoadJob")
                                .status("STARTED")
                                .startTime(LocalDateTime.now())
                                .details("File load started with params: " + jobExecution.getJobParameters())
                                .build();
                        jobRunLogRepository.save(log);
                        jobExecution.getExecutionContext().putLong("jobRunLogId", log.getId());
                    }

                    @Override
                    public void afterJob(JobExecution jobExecution) {
                        long id = jobExecution.getExecutionContext().getLong("jobRunLogId", -1L);
                        if (id > 0) {
                            JobRunLog log = jobRunLogRepository.findById(id).orElse(null);
                            if (log != null) {
                                log.setStatus(jobExecution.getStatus().name());
                                log.setEndTime(LocalDateTime.now());
                                log.setDetails("Exit status: " + jobExecution.getExitStatus());
                                jobRunLogRepository.save(log);
                            }
                        }
                    }
                })
                .start(fileLoadStep)
                .build();
    }
}


