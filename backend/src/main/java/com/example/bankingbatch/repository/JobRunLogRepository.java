package com.example.bankingbatch.repository;

import com.example.bankingbatch.domain.JobRunLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRunLogRepository extends JpaRepository<JobRunLog, Long> {
}


