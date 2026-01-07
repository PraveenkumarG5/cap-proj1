package com.example.bankingbatch.batch.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/**
 * Simple listener that logs start/end time and duration for each step.
 *
 * This is useful when processing large datasets (1M–10M records) to
 * understand which steps are taking the most time.
 */
public class StepTimingListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(StepTimingListener.class);

    private static final String STEP_START_TIME_KEY = "stepStartTimeMillis";

    @Override
    public void beforeStep(StepExecution stepExecution) {
        long start = System.currentTimeMillis();
        stepExecution.getExecutionContext().putLong(STEP_START_TIME_KEY, start);
        log.info("Step [{}] starting. ReadCount={}, WriteCount={}",
                stepExecution.getStepName(),
                stepExecution.getReadCount(),
                stepExecution.getWriteCount());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long start = stepExecution.getExecutionContext().getLong(STEP_START_TIME_KEY, -1L);
        if (start > 0) {
            long durationMillis = System.currentTimeMillis() - start;
            log.info("Step [{}] finished with status [{}] in {} ms. ReadCount={}, WriteCount={}, SkipCount={}",
                    stepExecution.getStepName(),
                    stepExecution.getStatus(),
                    durationMillis,
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getSkipCount());
        } else {
            log.info("Step [{}] finished with status [{}]", stepExecution.getStepName(), stepExecution.getStatus());
        }
        return stepExecution.getExitStatus();
    }
}


