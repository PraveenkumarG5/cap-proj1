package com.example.bankingbatch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Staging table for inbound transactions (to be processed by daily job).
@Entity
@Table(name = "transaction_staging", uniqueConstraints = {
        @UniqueConstraint(name = "uk_txn_id", columnNames = "txn_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_id", nullable = false, length = 100)
    private String txnId;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction; // CREDIT or DEBIT

    @Column(name = "processed_flag", nullable = false)
    private boolean processedFlag = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}


