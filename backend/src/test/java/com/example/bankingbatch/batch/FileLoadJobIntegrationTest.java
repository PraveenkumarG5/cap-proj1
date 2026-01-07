package com.example.bankingbatch.batch;

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

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest
@AutoConfigureTestDatabase
@ActiveProfiles("h2")
class FileLoadJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job fileLoadJob;

    @Autowired
    private TransactionStagingRepository stagingRepository;

    @Test
    void fileLoadJobLoadsCsv() throws Exception {
        Path tempFile = Files.createTempFile("transactions", ".csv");
        try (FileWriter writer = new FileWriter(tempFile.toFile())) {
            writer.write("txnId,accountNumber,amount,direction\n");
            writer.write("CSV-TXN-1,ACC-1001,50.00,CREDIT\n");
        }

        jobLauncherTestUtils.setJob(fileLoadJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addLong("timestamp", System.currentTimeMillis())
                        .addString("filePath", tempFile.toAbsolutePath().toString())
                        .toJobParameters());

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(stagingRepository.findAll()).isNotEmpty();
    }
}


