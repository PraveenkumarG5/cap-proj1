package com.example.bankingbatch.web;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

// Exposes REST endpoints to trigger the three batch jobs.
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobLauncher jobLauncher;
    private final Job dailyTransactionJob;
    private final Job monthlyInterestJob;
    private final Job fileLoadJob;

    public JobController(JobLauncher jobLauncher,
                         @Qualifier("dailyTransactionJob") Job dailyTransactionJob,
                         @Qualifier("monthlyInterestJob") Job monthlyInterestJob,
                         @Qualifier("fileLoadJob") Job fileLoadJob) {
        this.jobLauncher = jobLauncher;
        this.dailyTransactionJob = dailyTransactionJob;
        this.monthlyInterestJob = monthlyInterestJob;
        this.fileLoadJob = fileLoadJob;
    }

    @PostMapping("/daily")
    public ResponseEntity<String> triggerDailyJob() throws Exception {
        jobLauncher.run(dailyTransactionJob,
                new JobParametersBuilder()
                        .addLong("timestamp", Instant.now().toEpochMilli())
                        .toJobParameters());
        return ResponseEntity.accepted().body("dailyTransactionJob triggered");
    }

    @PostMapping("/interest")
    public ResponseEntity<String> triggerInterestJob() throws Exception {
        jobLauncher.run(monthlyInterestJob,
                new JobParametersBuilder()
                        .addLong("timestamp", Instant.now().toEpochMilli())
                        .toJobParameters());
        return ResponseEntity.accepted().body("monthlyInterestJob triggered");
    }

    @PostMapping("/load-file")
    public ResponseEntity<String> triggerFileLoad(@RequestParam("path") String path) throws Exception {
        jobLauncher.run(fileLoadJob,
                new JobParametersBuilder()
                        .addLong("timestamp", Instant.now().toEpochMilli())
                        .addString("filePath", path)
                        .toJobParameters());
        return ResponseEntity.accepted().body("fileLoadJob triggered for path " + path);
    }
}


