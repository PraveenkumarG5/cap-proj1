package com.example.bankingbatch.web;

import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobExecution;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Lightweight dashboard APIs for listing job instances.
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final JobExplorer jobExplorer;

    public DashboardController(JobExplorer jobExplorer) {
        this.jobExplorer = jobExplorer;
    }

    @GetMapping("/instances")
    public List<Map<String, Object>> jobInstances() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String jobName : jobExplorer.getJobNames()) {
            int start = 0;
            int count = 20;
            List<JobInstance> instances = jobExplorer.getJobInstances(jobName, start, count);
            for (JobInstance instance : instances) {
                JobExecution lastExecution = jobExplorer.getLastJobExecution(instance);
                Map<String, Object> map = new HashMap<>();
                map.put("jobName", jobName);
                map.put("instanceId", instance.getInstanceId());
                if (lastExecution != null) {
                    map.put("status", lastExecution.getStatus().toString());
                    map.put("startTime", lastExecution.getStartTime());
                    map.put("endTime", lastExecution.getEndTime());
                    long recordsProcessed = lastExecution.getStepExecutions().stream()
                            .mapToLong(stepExecution -> stepExecution.getWriteCount())
                            .sum();
                    map.put("recordsProcessed", recordsProcessed);
                }
                result.add(map);
            }
        }
        return result;
    }
}


