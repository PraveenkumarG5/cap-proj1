package com.example.bankingbatch.batch;

import com.example.bankingbatch.domain.Account;
import com.example.bankingbatch.domain.TransactionStaging;
import com.example.bankingbatch.repository.AccountRepository;
import com.example.bankingbatch.repository.AuditLogRepository;
import com.example.bankingbatch.repository.TransactionStagingRepository;
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
class DailyTransactionJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job dailyTransactionJob;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionStagingRepository stagingRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void dailyTransactionJobProcessesTransactions() throws Exception {
        Account account = accountRepository.findByAccountNumber("ACC-1001").orElseThrow();
        BigDecimal originalBalance = account.getBalance();

        TransactionStaging staging = TransactionStaging.builder()
                .txnId("TEST-TXN-1")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("100.00"))
                .direction("CREDIT")
                .processedFlag(false)
                .build();
        stagingRepository.save(staging);

        jobLauncherTestUtils.setJob(dailyTransactionJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters());

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        Account updated = accountRepository.findByAccountNumber("ACC-1001").orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(originalBalance.add(new BigDecimal("100.00")));

        assertThat(auditLogRepository.findAll()).isNotEmpty();
    }
}


