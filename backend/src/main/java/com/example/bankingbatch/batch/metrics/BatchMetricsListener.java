package com.example.bankingbatch.batch.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

// Micrometer listener to capture job starts, completions, and duration.
// Also logs job timings so that operators can see how long each job run took.
public class BatchMetricsListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(BatchMetricsListener.class);

    private final MeterRegistry meterRegistry;

    public BatchMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        meterRegistry.counter("batch.job.starts", "job", jobName).increment();
        jobExecution.getExecutionContext().putLong("startTimeMillis", System.currentTimeMillis());
        log.info("Job [{}] starting with parameters {}", jobName, jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();
        meterRegistry.counter("batch.job.completions", "job", jobName, "status", status.name()).increment();

        long startTime = jobExecution.getExecutionContext().getLong("startTimeMillis", -1L);
        if (startTime > 0) {
            long durationMillis = System.currentTimeMillis() - startTime;
            Timer.builder("batch.job.duration")
                    .tag("job", jobName)
                    .tag("status", status.name())
                    .register(meterRegistry)
                    .record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("Job [{}] completed with status [{}] in {} ms", jobName, status, durationMillis);
        } else {
            log.info("Job [{}] completed with status [{}]", jobName, status);
        }
    }
}



