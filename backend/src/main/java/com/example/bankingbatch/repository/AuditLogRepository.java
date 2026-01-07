package com.example.bankingbatch.repository;

import com.example.bankingbatch.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}


