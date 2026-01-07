package com.example.bankingbatch.batch;

import com.example.bankingbatch.domain.Account;
import com.example.bankingbatch.repository.AccountRepository;
import com.example.bankingbatch.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest
@AutoConfigureTestDatabase
@ActiveProfiles("h2")
class MonthlyInterestJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job monthlyInterestJob;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void monthlyInterestJobAppliesInterest() throws Exception {
        Account account = accountRepository.findByAccountNumber("ACC-1001").orElseThrow();
        BigDecimal original = account.getBalance();

        jobLauncherTestUtils.setJob(monthlyInterestJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters());

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        Account updated = accountRepository.findByAccountNumber("ACC-1001").orElseThrow();
        BigDecimal expectedMin = original.multiply(new BigDecimal("1.05"));
        assertThat(updated.getBalance()).isGreaterThanOrEqualTo(expectedMin);
        assertThat(auditLogRepository.findAll()).isNotEmpty();
    }
}


