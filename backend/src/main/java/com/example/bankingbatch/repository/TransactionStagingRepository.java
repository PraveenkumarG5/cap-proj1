package com.example.bankingbatch.repository;

import com.example.bankingbatch.domain.TransactionStaging;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionStagingRepository extends JpaRepository<TransactionStaging, Long> {
}


