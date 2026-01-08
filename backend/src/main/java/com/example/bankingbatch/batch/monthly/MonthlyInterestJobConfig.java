package com.example.bankingbatch.batch.monthly;

import com.example.bankingbatch.batch.metrics.StepTimingListener;
import com.example.bankingbatch.domain.Account;
import com.example.bankingbatch.domain.AuditLog;
import com.example.bankingbatch.repository.AccountRepository;
import com.example.bankingbatch.repository.AuditLogRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

// Configures the Monthly Interest job that applies +5% interest to ACTIVE accounts.
@Configuration
public class MonthlyInterestJobConfig {

    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public MonthlyInterestJobConfig(AccountRepository accountRepository,
                                    AuditLogRepository auditLogRepository,
                                    JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    /**
     * Listener to log timing for the monthly interest step.
     */
    @Bean
    public StepTimingListener monthlyStepTimingListener() {
        return new StepTimingListener();
    }

    @Bean
    @StepScope
    public ItemReader<Account> monthlyInterestReader() {
        List<Account> activeAccounts = accountRepository.findByStatus("ACTIVE");
        return new ListItemReader<>(activeAccounts);
    }

    @Bean
    public org.springframework.batch.item.ItemProcessor<Account, Account> monthlyInterestProcessor() {
        return account -> {
            // Compute 5% interest with HALF_EVEN rounding.
            BigDecimal interest = account.getBalance()
                    .multiply(new BigDecimal("0.05"))
                    .setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal newBalance = account.getBalance().add(interest).setScale(2, RoundingMode.HALF_EVEN);
            account.setBalance(newBalance);

            AuditLog log = AuditLog.builder()
                    .eventType("MONTHLY_INTEREST")
                    .reference(account.getAccountNumber())
                    .payload("{\"interest\":" + interest + ",\"newBalance\":" + newBalance + "}")
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(log);

            return account;
        };
    }

    @Bean
    public ItemWriter<Account> monthlyInterestWriter() {
        return items -> accountRepository.saveAll(items);
    }

    @Bean
    public Step monthlyInterestStep(ItemReader<Account> monthlyInterestReader,
                                    @Qualifier("monthlyStepTimingListener") StepTimingListener monthlyStepTimingListener) {
        return new StepBuilder("monthlyInterestStep", jobRepository)
                .<Account, Account>chunk(100, transactionManager)
                .reader(monthlyInterestReader)
                .processor(monthlyInterestProcessor())
                .writer(monthlyInterestWriter())
                .listener(monthlyStepTimingListener)
                .build();
    }

    @Bean
    public Job monthlyInterestJob(@Qualifier("monthlyInterestStep") Step monthlyInterestStep) {
        return new JobBuilder("monthlyInterestJob", jobRepository)
                .start(monthlyInterestStep)
                .build();
    }
}


