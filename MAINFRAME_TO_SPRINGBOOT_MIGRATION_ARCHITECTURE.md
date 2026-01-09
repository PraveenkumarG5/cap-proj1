# Mainframe to Java Spring Boot Migration Architecture Document

**Version:** 1.0  
**Date:** January 8, 2026  
**Status:** Architecture Design  

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current State Analysis](#current-state-analysis)
3. [Target Architecture](#target-architecture)
4. [Component Mapping](#component-mapping)
5. [Technical Stack](#technical-stack)
6. [Detailed Architecture](#detailed-architecture)
7. [Data Migration Strategy](#data-migration-strategy)
8. [Integration & Interfaces](#integration--interfaces)
9. [Deployment Architecture](#deployment-architecture)
10. [Implementation Roadmap](#implementation-roadmap)
11. [Risk Management](#risk-management)
12. [Security & Compliance](#security--compliance)

---

## Executive Summary

### Project Overview
This document outlines the comprehensive migration strategy for transforming a legacy mainframe-based banking batch processing application to a modern, cloud-ready Java Spring Boot microservices architecture with Spring Batch for batch processing.

### Current Environment
- **Mainframe System:** IBM mainframe with COBOL batch and online programs
- **Data Format:** EBCDIC copybook structures
- **Job Scheduling:** JCL (Job Control Language) / Proc procedures
- **Control Mechanism:** CTC (Control cards) for job parameters
- **Batch Programs:** COBOL SRD (Batch processing)
- **Online Programs:** COBOL SRK (Interactive/Online)

### Target Environment
- **Platform:** Java 21 LTS with Spring Boot 3.2.x
- **Batch Processing:** Spring Batch 5.x for job orchestration
- **Database:** PostgreSQL with in-memory H2 for testing
- **Deployment:** Docker containers on Kubernetes
- **API Layer:** RESTful services with Spring Web

### Key Benefits
- **Modernization:** Move from proprietary mainframe to open-source Java ecosystem
- **Scalability:** Horizontal scaling through containerization
- **Cost:** Reduced licensing and infrastructure costs
- **Flexibility:** Faster feature deployment and modifications
- **Maintenance:** Larger talent pool with Java skills
- **Cloud-Ready:** Native cloud deployment capabilities

---

## Current State Analysis

### 1. Mainframe Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MAINFRAME SYSTEM                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────────┐         ┌──────────────────────┐ │
│  │   JCL/Proc Jobs     │         │   CTC Control Cards  │ │
│  │ - Batch Scheduling  │────────▶│ - Parameter Input    │ │
│  │ - Job Steps         │         │ - Control Flags      │ │
│  └──────────────────────┘         └──────────────────────┘ │
│           │                                    │            │
│           ▼                                    ▼            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │      COBOL Batch Programs (SRD)                      │  │
│  │ - Daily Transaction Processing                       │  │
│  │ - Monthly Interest Calculation                       │  │
│  │ - File Load Operations                               │  │
│  │ - Report Generation                                  │  │
│  └──────────────────────────────────────────────────────┘  │
│           │                                                 │
│           ▼                                                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │    COBOL Online Programs (SRK)                       │  │
│  │ - Account Inquiry                                    │  │
│  │ - Transaction Entry                                  │  │
│  │ - Balance Verification                               │  │
│  └──────────────────────────────────────────────────────┘  │
│           │                                                 │
│           ▼                                                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  VSAM/DB2 Data Store (EBCDIC Copybook Format)       │  │
│  │ - Account Master                                     │  │
│  │ - Transaction Records                                │  │
│  │ - Audit Logs                                         │  │
│  │ - Reference Data                                     │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2. Current Program Breakdown

#### Batch Programs (SRD - COBOL Batch)
| Program | Function | Trigger | Frequency |
|---------|----------|---------|-----------|
| DLY-TXN-001 | Daily Transaction Processing | JCL/CTC | Daily |
| MNT-INT-001 | Monthly Interest Calculation | JCL/CTC | Monthly |
| FIL-LOD-001 | File Load Operations | JCL/CTC | Ad-hoc |
| RPT-GEN-001 | Report Generation | JCL/CTC | Weekly |

#### Online Programs (SRK - COBOL Online)
| Program | Function | Access | Users |
|---------|----------|--------|-------|
| ACC-INQ-001 | Account Inquiry | Terminal | Tellers |
| TXN-ENT-001 | Transaction Entry | Terminal | Officers |
| BAL-VER-001 | Balance Verification | Terminal | Supervisors |

### 3. Data Structures (Copybooks)
- **ACC-RECORD:** Account Master copybook (account number, balance, status)
- **TXN-RECORD:** Transaction copybook (txn ID, account, amount, direction)
- **CTL-RECORD:** Control record copybook (batch parameters, control flags)

---

## Target Architecture

### High-Level Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         SPRING BOOT APPLICATION LAYER                      │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │  REST API Layer  │  │  Batch Job Mgmt  │  │  File Load Service       │ │
│  │ - HTTP Endpoints │  │  - Job Launcher  │  │ - CSV/File Processing    │ │
│  │ - Controllers    │  │  - Job Monitor   │  │ - Validation & Transform │ │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────┘ │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    SPRING BATCH PROCESSING LAYER                           │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ Daily Transaction Job (SRD Equivalent)                              │ │
│  │ - Reader: JPA Query from staging table                              │ │
│  │ - Processor: Apply business logic, account updates                  │ │
│  │ - Writer: Persist updated accounts, audit logs                      │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ Monthly Interest Job (SRD Equivalent)                               │ │
│  │ - Reader: List active accounts                                      │ │
│  │ - Processor: Calculate interest, apply to accounts                  │ │
│  │ - Writer: Update balances, create interest records                  │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ File Load Job (SRD Equivalent)                                      │ │
│  │ - Reader: FlatFileItemReader (CSV/fixed-width)                      │ │
│  │ - Processor: Parse, validate, transform to domain objects           │ │
│  │ - Writer: JDBC batch insert to staging table                        │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                       BUSINESS LOGIC LAYER                                 │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │ Account Service  │  │ Transaction Svc  │  │ Audit Service            │ │
│  │ - Account Logic  │  │ - Transaction    │  │ - Event Logging          │ │
│  │ - Balance Calc   │  │  Processing      │  │ - Compliance Tracking    │ │
│  │ - Account Status │  │ - Validation     │  │ - Report Generation      │ │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────┘ │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                       DATA ACCESS LAYER (JPA/Repositories)                 │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ Spring Data JPA Repositories                                         │ │
│  │ - AccountRepository                                                  │ │
│  │ - TransactionRepository                                              │ │
│  │ - TransactionStagingRepository                                       │ │
│  │ - AuditLogRepository                                                 │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    DATABASE LAYER (PostgreSQL/H2)                          │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │ accounts         │  │ transactions     │  │ transaction_staging      │ │
│  │ - account_number │  │ - txn_id         │  │ - txn_id                 │ │
│  │ - balance        │  │ - account_number │  │ - account_number         │ │
│  │ - status         │  │ - amount         │  │ - amount                 │ │
│  │ - created_at     │  │ - direction      │  │ - direction              │ │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────┘ │
│                                                                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │ audit_logs       │  │ batch_job_instance                              │ │
│  │ - id             │  │ (Spring Batch metadata)                          │ │
│  │ - event_type     │  │                                                  │ │
│  │ - reference      │  │ batch_job_execution                              │ │
│  │ - payload        │  │ (Spring Batch metadata)                          │ │
│  │ - created_at     │  │                                                  │ │
│  └──────────────────┘  └──────────────────────────────────────────────────┘ │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Mapping

### Mainframe to Spring Boot Component Mapping Table

| Mainframe Component | Type | Spring Boot Equivalent | Implementation |
|-------------------|------|----------------------|-----------------|
| JCL/Proc Jobs | Job Definition | Spring Batch Job | @Bean Job configuration |
| CTC Control Cards | Parameter Input | Job Parameters | JobParameters with step scope |
| COBOL Batch (SRD) | Business Logic | Spring Batch Steps | ItemReader/Processor/Writer |
| COBOL Online (SRK) | Interactive Logic | REST Controllers | @RestController |
| VSAM/DB2 DB | Data Store | PostgreSQL | JPA Entity/Repository |
| EBCDIC Copybook | Data Format | Java POJOs | @Entity domain objects |
| Report Generation | Output | Dashboard API | /api/reports endpoints |
| Scheduling | Cron Jobs | Quartz/Spring Task | @Scheduled or external scheduler |

### Detailed Component Mapping

#### 1. Daily Transaction Job (SRD → Spring Batch)

**Mainframe (COBOL SRD):**
```cobol
IDENTIFICATION DIVISION.
PROGRAM-ID. DLY-TXN-001.
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-TRANSACTION-RECORD.
   05 TXN-ID PIC 9(8).
   05 ACCOUNT-NUMBER PIC X(20).
   05 AMOUNT PIC 9(15)V99.
   05 DIRECTION PIC X(6) (CREDIT/DEBIT).
PROCEDURE DIVISION.
   PERFORM PROCESS-TRANSACTIONS.
   PERFORM UPDATE-ACCOUNTS.
   PERFORM WRITE-AUDIT-LOG.
```

**Spring Boot (Java + Spring Batch):**
```java
@Configuration
@EnableBatchProcessing
public class DailyTransactionJobConfig {
    
    @Bean
    public Job dailyTransactionJob(Step dailyTransactionStep) {
        return new JobBuilder("dailyTransactionJob", jobRepository)
            .start(dailyTransactionStep)
            .listener(new BatchMetricsListener(meterRegistry))
            .build();
    }
    
    @Bean
    public Step dailyTransactionStep(
            JpaPagingItemReader<TransactionStaging> reader,
            ItemProcessor<TransactionStaging, TransactionStaging> processor,
            ItemWriter<TransactionStaging> writer) {
        return new StepBuilder("dailyTransactionStep", jobRepository)
            .<TransactionStaging, TransactionStaging>chunk(100, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .listener(new StepTimingListener())
            .build();
    }
    
    @Bean
    @StepScope
    public JpaPagingItemReader<TransactionStaging> dailyTransactionReader() {
        return new JpaPagingItemReaderBuilder<TransactionStaging>()
            .name("dailyTransactionReader")
            .entityManagerFactory(entityManagerFactory)
            .pageSize(100)
            .queryString("SELECT t FROM TransactionStaging t WHERE t.processedFlag = false")
            .build();
    }
    
    @Bean
    public ItemProcessor<TransactionStaging, TransactionStaging> dailyTransactionProcessor() {
        return item -> {
            // Business logic equivalent to COBOL processing
            Account account = accountRepository.findByAccountNumber(item.getAccountNumber())
                .orElseThrow();
            BigDecimal newBalance = calculateNewBalance(account, item);
            account.setBalance(newBalance);
            accountRepository.save(account);
            auditLogRepository.save(createAuditLog(item));
            item.setProcessedFlag(true);
            return item;
        };
    }
}
```

#### 2. File Load Job (SRD → Spring Batch)

**Mainframe (JCL with file input):**
```jcl
//FILELOAD JOB (ACCT,BANKING),'FILE LOAD',CLASS=B,MSGCLASS=X
//STEP1 EXEC PGM=FIL-LOD-001,PARM='TRANSACTIONS.TXT'
//INFILE DD DSN=TRANSACTIONS.TXT,DISP=SHR
//OUTFILE DD DSN=LOADED.FILE,DISP=NEW
```

**Spring Boot (Java + Spring Batch):**
```java
@Configuration
public class FileLoadJobConfig {
    
    @Bean
    public Job fileLoadJob(Step fileLoadStep) {
        return new JobBuilder("fileLoadJob", jobRepository)
            .start(fileLoadStep)
            .listener(jobRunLogListener())
            .build();
    }
    
    @Bean
    public Step fileLoadStep(
            FlatFileItemReader<TransactionStaging> reader,
            ItemProcessor<TransactionStaging, TransactionStaging> processor,
            JdbcBatchItemWriter<TransactionStaging> writer) {
        return new StepBuilder("fileLoadStep", jobRepository)
            .<TransactionStaging, TransactionStaging>chunk(1000, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
    
    @Bean
    @StepScope
    public FlatFileItemReader<TransactionStaging> fileLoadReader(
            @Value("#{jobParameters['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<TransactionStaging>()
            .name("fileLoadReader")
            .resource(new FileSystemResource(filePath))
            .delimited()
            .names(new String[]{"txnId", "accountNumber", "amount", "direction"})
            .fieldSetMapper(new BeanWrapperFieldSetMapper<TransactionStaging>() {{
                setTargetType(TransactionStaging.class);
            }})
            .build();
    }
}
```

#### 3. Monthly Interest Job (SRD → Spring Batch)

**Implementation Pattern:**
- Read all active accounts from database
- Calculate monthly interest (e.g., 0.5% APR)
- Update account balances
- Create interest transaction records
- Log audit entries

```java
@Bean
public Job monthlyInterestJob(Step monthlyInterestStep) {
    return new JobBuilder("monthlyInterestJob", jobRepository)
        .start(monthlyInterestStep)
        .build();
}

@Bean
public Step monthlyInterestStep(
        ItemReader<Account> reader,
        ItemProcessor<Account, Account> processor,
        ItemWriter<Account> writer) {
    return new StepBuilder("monthlyInterestStep", jobRepository)
        .<Account, Account>chunk(100, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .build();
}

@Bean
@StepScope
public ItemReader<Account> monthlyInterestReader() {
    return new ListItemReader<>(
        accountRepository.findAllActive()
    );
}

@Bean
public ItemProcessor<Account, Account> monthlyInterestProcessor() {
    return account -> {
        BigDecimal rate = new BigDecimal("0.005"); // 0.5% APR
        BigDecimal monthlyRate = rate.divide(new BigDecimal("12"), 4, RoundingMode.HALF_EVEN);
        BigDecimal interest = account.getBalance()
            .multiply(monthlyRate)
            .setScale(2, RoundingMode.HALF_EVEN);
        
        account.setBalance(account.getBalance().add(interest));
        auditLogRepository.save(AuditLog.builder()
            .eventType("MONTHLY_INTEREST")
            .reference(account.getAccountNumber())
            .payload(String.format("{\"interest\": %.2f}", interest))
            .createdAt(LocalDateTime.now())
            .build());
        return account;
    };
}
```

---

## Technical Stack

### Backend Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Language | Java | 21 LTS | Core language |
| Framework | Spring Boot | 3.2.2 | Application framework |
| Batch Processing | Spring Batch | 5.1.0 | Job orchestration |
| Data Access | Spring Data JPA | 3.2.2 | ORM/Repository pattern |
| Database Driver | PostgreSQL JDBC | 42.x | Database connectivity |
| API Framework | Spring Web MVC | 6.1.3 | REST endpoint support |
| Build Tool | Maven | 3.9.x | Build & dependency management |
| Monitoring | Micrometer + Prometheus | 1.12.x | Metrics collection |
| Logging | SLF4J + Logback | 2.x | Logging framework |

### Database Stack

| Aspect | Production | Development |
|--------|-----------|-------------|
| Database | PostgreSQL 14+ | H2 In-Memory |
| Connection Pool | HikariCP | HikariCP |
| Migration Tool | Liquibase/Flyway | Spring SQL init |
| Backup | PostgreSQL pg_dump | N/A |

### DevOps Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Containerization | Docker | Application packaging |
| Orchestration | Kubernetes | Container orchestration |
| CI/CD | Jenkins/GitHub Actions | Build & deploy automation |
| Configuration | Spring Cloud Config | Environment management |
| Monitoring | Prometheus + Grafana | Performance metrics |
| Logging | ELK Stack | Centralized logging |

---

## Detailed Architecture

### 1. Batch Processing Architecture

#### Job Execution Flow

```
┌─────────────────────────────────────────────────────────────┐
│ Spring Batch Job Execution Flow                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Job Start                                                  │
│    │                                                        │
│    ▼                                                        │
│  Step 1: dailyTransactionStep                             │
│    │                                                        │
│    ├─▶ ItemReader (JPA Query)                             │
│    │   - SELECT * FROM transaction_staging WHERE ...      │
│    │   - Page size: 100 records                            │
│    │                                                        │
│    ├─▶ ItemProcessor (Business Logic)                     │
│    │   - Load account                                       │
│    │   - Validate transaction                              │
│    │   - Apply credit/debit                                │
│    │   - Check balance constraints                          │
│    │   - Create audit log                                   │
│    │   - Mark transaction as processed                      │
│    │   - Error handling: Log & skip invalid records         │
│    │                                                        │
│    ├─▶ ItemWriter (Persist Changes)                       │
│    │   - Save updated accounts                             │
│    │   - Save audit logs                                    │
│    │   - Update staging table                              │
│    │   - Batch write (100 records per batch)               │
│    │                                                        │
│    └─▶ Repeat until all records processed                  │
│                                                             │
│  Job Completion                                             │
│    └─▶ Log job statistics                                  │
│        - Total read: X                                      │
│        - Successfully written: Y                            │
│        - Failed/skipped: Z                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### Error Handling & Retry Strategy

```java
@Bean
public Step dailyTransactionStep(...) {
    return new StepBuilder("dailyTransactionStep", jobRepository)
        .<TransactionStaging, TransactionStaging>chunk(100, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .faultTolerant()
        .skipLimit(100)
        .skip(InsufficientFundsException.class)
        .skip(IllegalArgumentException.class)
        .retry(OptimisticLockingFailureException.class)
        .retryLimit(3)
        .listener(new ChunkListener())
        .build();
}
```

**Error Handling Strategy:**
- **Skippable Errors:** Business validation errors (insufficient funds, invalid account)
- **Retryable Errors:** Transient database errors, connection timeouts
- **Fatal Errors:** Data integrity violations, schema mismatches
- **Logging:** All errors logged with context (transaction ID, error reason, timestamp)

### 2. REST API Layer (Online Equivalent - SRK)

**Endpoint Mapping (COBOL Online → REST API):**

| COBOL Program | Mainframe Screen | REST Endpoint | Method | Purpose |
|---------------|-----------------|--------------|--------|---------|
| ACC-INQ-001 | Account Inquiry | /api/accounts/{id} | GET | Retrieve account details |
| TXN-ENT-001 | Transaction Entry | /api/transactions | POST | Create new transaction |
| BAL-VER-001 | Balance Verification | /api/accounts/{id}/balance | GET | Get current balance |

**Sample REST APIs:**

```java
@RestController
@RequestMapping("/api")
public class AccountController {
    
    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<AccountDTO> getAccount(
            @PathVariable String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return ResponseEntity.ok(mapToDTO(account));
    }
    
    @PostMapping("/transactions")
    public ResponseEntity<TransactionDTO> createTransaction(
            @RequestBody TransactionRequest request) {
        // Validate and persist transaction
        Transaction transaction = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToDTO(transaction));
    }
}

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    
    @PostMapping("/daily")
    public ResponseEntity<JobExecutionResponse> triggerDailyJob() {
        JobExecution execution = jobLauncher.run(
            dailyTransactionJob,
            new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters()
        );
        return ResponseEntity.ok(mapToResponse(execution));
    }
    
    @PostMapping("/load-file")
    public ResponseEntity<JobExecutionResponse> triggerFileLoad(
            @RequestParam String filePath) {
        JobExecution execution = jobLauncher.run(
            fileLoadJob,
            new JobParametersBuilder()
                .addString("filePath", filePath)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters()
        );
        return ResponseEntity.ok(mapToResponse(execution));
    }
    
    @GetMapping("/job-status/{jobExecutionId}")
    public ResponseEntity<JobStatusDTO> getJobStatus(
            @PathVariable Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        return ResponseEntity.ok(mapToStatusDTO(execution));
    }
}
```

### 3. Domain Model & Entity Mapping

**COBOL Copybook → JPA Entity:**

```java
@Entity
@Table(name = "accounts")
@Data
@Builder
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String accountNumber;      // From COBOL: ACCOUNT-NUMBER
    
    @Column(precision = 19, scale = 2)
    private BigDecimal balance;        // From COBOL: BALANCE
    
    @Enumerated(EnumType.STRING)
    private AccountStatus status;      // From COBOL: ACCOUNT-STATUS
    
    @Column(nullable = false)
    private LocalDateTime createdAt;   // From COBOL: CREATE-DATE
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;   // From COBOL: UPDATE-DATE
}

@Entity
@Table(name = "transaction_staging")
@Data
@Builder
public class TransactionStaging {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String txnId;              // From COBOL: TXN-ID
    
    @Column(nullable = false)
    private String accountNumber;      // From COBOL: ACCOUNT-NUMBER
    
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;         // From COBOL: AMOUNT
    
    @Column(nullable = false)
    private String direction;          // From COBOL: DIRECTION (CREDIT/DEBIT)
    
    @Column(columnDefinition = "boolean default false")
    private Boolean processedFlag;     // Processing indicator
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
}

@Entity
@Table(name = "audit_logs")
@Data
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String eventType;          // DAILY_TXN, MONTHLY_INTEREST, FILE_LOAD
    
    @Column(nullable = false)
    private String reference;          // Account number, transaction ID
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;            // JSON event details
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

### 4. Database Schema Design

```sql
-- Accounts Table (equivalent to COBOL master record)
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(20) UNIQUE NOT NULL,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    INDEX idx_account_number (account_number)
);

-- Transaction Staging Table (input buffer for batch processing)
CREATE TABLE transaction_staging (
    id BIGSERIAL PRIMARY KEY,
    txn_id VARCHAR(50) NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('CREDIT', 'DEBIT')),
    processed_flag BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (account_number) REFERENCES accounts(account_number),
    INDEX idx_processed (processed_flag)
);

-- Audit Log Table (immutable audit trail)
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    reference VARCHAR(100) NOT NULL,
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    INDEX idx_event_type (event_type),
    INDEX idx_reference (reference)
);

-- Spring Batch Metadata Tables
CREATE TABLE batch_job_instance (
    JOB_INSTANCE_ID BIGSERIAL PRIMARY KEY,
    VERSION BIGINT,
    JOB_NAME VARCHAR(100) NOT NULL,
    JOB_KEY VARCHAR(2500) NOT NULL,
    UNIQUE (JOB_NAME, JOB_KEY)
);

CREATE TABLE batch_job_execution (
    JOB_EXECUTION_ID BIGSERIAL PRIMARY KEY,
    VERSION BIGINT,
    JOB_INSTANCE_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP,
    END_TIME TIMESTAMP,
    STATUS VARCHAR(10),
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE TEXT,
    FOREIGN KEY (JOB_INSTANCE_ID) REFERENCES batch_job_instance(JOB_INSTANCE_ID)
);

CREATE TABLE batch_step_execution (
    STEP_EXECUTION_ID BIGSERIAL PRIMARY KEY,
    VERSION BIGINT,
    STEP_NAME VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP,
    END_TIME TIMESTAMP,
    STATUS VARCHAR(10),
    COMMIT_COUNT BIGINT,
    READ_COUNT BIGINT,
    FILTER_COUNT BIGINT,
    WRITE_COUNT BIGINT,
    READ_SKIP_COUNT BIGINT,
    WRITE_SKIP_COUNT BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT BIGINT,
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE TEXT,
    FOREIGN KEY (JOB_EXECUTION_ID) REFERENCES batch_job_execution(JOB_EXECUTION_ID)
);
```

---

## Data Migration Strategy

### Phase 1: Data Extraction from Mainframe

**COBOL Copybook Export:**
- Export VSAM/DB2 data in fixed-width or CSV format
- Handle EBCDIC character conversion to UTF-8
- Preserve data integrity with checksums
- Create migration audit trail

**Example COBOL Extract Program:**
```cobol
IDENTIFICATION DIVISION.
PROGRAM-ID. EXTRACT-DATA.
DATA DIVISION.
FILE SECTION.
FD ACCOUNT-FILE.
01 ACCOUNT-RECORD FROM COPYBOOK.
FD OUTPUT-FILE.
01 OUTPUT-RECORD.
PROCEDURE DIVISION.
    PERFORM READ-AND-CONVERT
        UNTIL EOF.
    STOP RUN.
    
READ-AND-CONVERT.
    READ ACCOUNT-FILE
        AT END MOVE 'Y' TO EOF-FLAG
        NOT AT END
            MOVE ACCOUNT-ID TO OUT-ACCOUNT-ID
            MOVE ACCOUNT-BALANCE TO OUT-BALANCE
            CONVERT EBCDIC TO ASCII
            WRITE OUTPUT-RECORD
    END-READ.
```

### Phase 2: Data Validation & Transformation

**Java Data Transformation:**
```java
@Component
public class DataMigrationService {
    
    public void migrateAccountsFromCsv(String csvPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse CSV: account_number,balance,status
                String[] fields = line.split(",");
                
                Account account = Account.builder()
                    .accountNumber(fields[0].trim())
                    .balance(new BigDecimal(fields[1].trim()))
                    .status(AccountStatus.valueOf(fields[2].trim()))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
                
                // Validate account
                validateAccount(account);
                
                // Save to PostgreSQL
                accountRepository.save(account);
            }
        }
    }
    
    private void validateAccount(Account account) {
        if (account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new DataValidationException("Invalid balance: " + account.getBalance());
        }
        if (account.getAccountNumber() == null || account.getAccountNumber().isEmpty()) {
            throw new DataValidationException("Account number required");
        }
    }
}
```

### Phase 3: Data Reconciliation

**Reconciliation Strategy:**
- Count verification: Compare row counts between mainframe and Spring Boot
- Sample verification: Random sampling of records for data accuracy
- Hash verification: Checksum validation of migrated data
- Difference reporting: Identify discrepancies for manual review

```java
@Component
public class DataReconciliationService {
    
    public ReconciliationReport reconcile() {
        ReconciliationReport report = new ReconciliationReport();
        
        // Count verification
        long mainframeCount = getMainframeRecordCount();
        long postgresCount = accountRepository.count();
        report.setCountMatch(mainframeCount == postgresCount);
        
        // Sample verification (random 100 records)
        List<String> sampleIds = getRandomSampleIds(100);
        for (String id : sampleIds) {
            Account postgresRecord = accountRepository.findByAccountNumber(id).get();
            String mainframeRecord = getMainframeRecord(id);
            if (!recordsMatch(postgresRecord, mainframeRecord)) {
                report.addDiscrepancy(id);
            }
        }
        
        return report;
    }
}
```

---

## Integration & Interfaces

### 1. External System Integrations

```
┌────────────────────────────┐
│  External Systems          │
├────────────────────────────┤
│                           │
│  ┌──────────────────────┐ │
│  │ Banking Core System  │ │
│  │ (REST API calls)     │ │
│  └──────────────────────┘ │
│           │               │
│           ▼               │
│  ┌──────────────────────┐ │
│  │ Spring Boot App      │ │
│  │ (/api/transactions)  │ │
│  └──────────────────────┘ │
│           ▲               │
│           │               │
│  ┌──────────────────────┐ │
│  │ Reporting System     │ │
│  │ (Poll job status)    │ │
│  └──────────────────────┘ │
│                           │
│  ┌──────────────────────┐ │
│  │ File Upload System   │ │
│  │ (S3/File shares)     │ │
│  └──────────────────────┘ │
│                           │
└────────────────────────────┘
```

### 2. API Contract Examples

**Account Query API (Equivalent to COBOL ACC-INQ-001):**
```json
GET /api/accounts/ACC-1001
Response 200 OK:
{
  "id": 1,
  "accountNumber": "ACC-1001",
  "balance": "50000.00",
  "status": "ACTIVE",
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-08T15:30:00Z"
}
```

**Transaction Creation API (Equivalent to COBOL TXN-ENT-001):**
```json
POST /api/transactions
Request Body:
{
  "accountNumber": "ACC-1001",
  "amount": "500.00",
  "direction": "DEBIT",
  "description": "ATM Withdrawal"
}

Response 201 Created:
{
  "id": 1,
  "txnId": "TXN-2026010801001",
  "accountNumber": "ACC-1001",
  "amount": "500.00",
  "direction": "DEBIT",
  "createdAt": "2026-01-08T15:30:45Z"
}
```

**Job Trigger API:**
```json
POST /api/jobs/daily
Response:
{
  "jobExecutionId": 123,
  "jobName": "dailyTransactionJob",
  "status": "STARTED",
  "startTime": "2026-01-08T15:30:00Z"
}

GET /api/jobs/123/status
Response:
{
  "jobExecutionId": 123,
  "jobName": "dailyTransactionJob",
  "status": "COMPLETED",
  "startTime": "2026-01-08T15:30:00Z",
  "endTime": "2026-01-08T15:35:45Z",
  "readCount": 10000,
  "writeCount": 9950,
  "skipCount": 50
}
```

---

## Deployment Architecture

### 1. Containerization (Docker)

**Dockerfile:**
```dockerfile
FROM openjdk:21-jdk-slim as builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src .
RUN mvn clean package -DskipTests

FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENTRYPOINT ["java", "$JAVA_OPTS", "-jar", "app.jar"]
```

**Docker Compose (Local Development):**
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: banking_db
      POSTGRES_USER: bankuser
      POSTGRES_PASSWORD: bankpass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/banking_db
      SPRING_DATASOURCE_USERNAME: bankuser
      SPRING_DATASOURCE_PASSWORD: bankpass
      SPRING_PROFILES_ACTIVE: postgres
    depends_on:
      - postgres

volumes:
  postgres_data:
```

### 2. Kubernetes Deployment

**Kubernetes Deployment Manifest:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: banking-app-config
data:
  application-prod.yml: |
    spring:
      datasource:
        url: jdbc:postgresql://postgres-service:5432/banking_db
        username: bankuser
        password: ${DB_PASSWORD}
      jpa:
        show-sql: false
        properties:
          hibernate:
            dialect: org.hibernate.dialect.PostgreSQLDialect
    logging:
      level:
        org.hibernate: WARN

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: banking-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: banking-app
  template:
    metadata:
      labels:
        app: banking-app
    spec:
      containers:
      - name: banking-app
        image: banking-app:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: password
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: banking-app-service
spec:
  selector:
    app: banking-app
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
```

---

## Implementation Roadmap

### Timeline: 12-Month Migration Project

#### Phase 1: Foundation (Months 1-2)
- **Week 1-2:** Project setup, environment configuration
  - Set up Spring Boot project structure
  - Configure Maven/Gradle build pipeline
  - Initialize PostgreSQL database
- **Week 3-4:** Data extraction from mainframe
  - Develop COBOL extract programs
  - Export VSAM/DB2 data to CSV
  - Validate data integrity
- **Week 5-6:** Database design & creation
  - Design PostgreSQL schema
  - Create JPA entities
  - Set up migration scripts
- **Week 7-8:** Build pipeline setup
  - Docker containerization
  - CI/CD pipeline (Jenkins/GitHub Actions)
  - Testing environment setup

#### Phase 2: Core Batch Jobs (Months 3-5)
- **Month 3:** Daily Transaction Job
  - Implement item reader/processor/writer
  - Error handling & retry logic
  - Unit & integration tests
- **Month 4:** Monthly Interest Job
  - Interest calculation logic
  - Batch update strategies
  - Reconciliation scripts
- **Month 5:** File Load Job
  - CSV parsing & validation
  - Performance optimization
  - Load testing

#### Phase 3: Online Services & APIs (Months 6-7)
- **Week 1-2:** REST API design & implementation
  - Account management endpoints
  - Transaction inquiry APIs
  - Job monitoring endpoints
- **Week 3-4:** UI dashboard
  - Job status monitoring
  - Transaction history
  - Account details view

#### Phase 4: Testing & UAT (Months 8-9)
- **Month 8:** Comprehensive testing
  - Unit tests (JUnit 5)
  - Integration tests
  - Performance tests (millions of records)
  - Security testing
- **Month 9:** User acceptance testing
  - Business user training
  - Parallel run with mainframe (reconciliation)
  - Issue resolution

#### Phase 5: Cutover & Optimization (Months 10-12)
- **Month 10:** Cutover planning & preparation
  - Final data migration
  - Fallback procedures
  - Monitoring setup
- **Month 11:** Cutover execution
  - Zero-downtime deployment strategy
  - Concurrent operations (mainframe + Spring Boot)
  - Reconciliation & validation
- **Month 12:** Post-cutover stabilization
  - Performance optimization
  - Production bug fixes
  - Knowledge transfer to operations team

### Deliverables by Phase

| Phase | Deliverable | Artifact |
|-------|-----------|----------|
| 1 | Database Schema | DDL scripts, ER diagram |
| 1 | Build Pipeline | Jenkins pipeline, Docker images |
| 2 | Batch Jobs | Source code, job configurations |
| 2 | Documentation | Job specifications, error codes |
| 3 | REST APIs | API documentation (Swagger/OpenAPI) |
| 3 | UI Dashboard | Frontend code, user guides |
| 4 | Test Reports | Test coverage reports, performance results |
| 5 | Production Deployment | Deployment scripts, runbooks |

---

## Risk Management

### Identified Risks & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Data Loss During Migration | Low | Critical | Backup mainframe data, validate checksums, parallel run period |
| Performance Degradation | Medium | High | Load testing with production data volumes, query optimization |
| Incomplete Feature Parity | Medium | High | Detailed requirements gathering, UAT phase with business users |
| Resource Constraints | Medium | Medium | Flexible timeline, outsourcing non-critical components |
| Integration Issues | Low | High | Comprehensive API testing, staged rollout |
| Regulatory Compliance | Low | Critical | Audit trail implementation, compliance testing, legal review |

### Testing Strategy

**Test Coverage:**
- Unit tests: 80%+ code coverage (JUnit 5, Mockito)
- Integration tests: Job execution, database operations
- Performance tests: 1 million+ records, batch operations
- Security tests: OWASP Top 10 vulnerabilities
- Compliance tests: Audit logging, data retention

**Load Testing Scenarios:**
```
Scenario 1: Daily batch processing (10,000 transactions)
- Expected duration: < 5 minutes
- Success rate: > 99.9%

Scenario 2: File load (1 million records)
- Expected duration: < 30 minutes
- Throughput: 30,000+ records/minute

Scenario 3: Monthly interest calculation (50,000 accounts)
- Expected duration: < 10 minutes
- Success rate: 100%
```

---

## Security & Compliance

### Security Architecture

```
┌────────────────────────────────────────────────────────────┐
│ Security Layers                                            │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ Layer 1: Network Security                                 │
│  - VPC isolation                                           │
│  - Security groups (ingress/egress rules)                  │
│  - DDoS protection                                         │
│                                                            │
│ Layer 2: API Security                                      │
│  - TLS/SSL encryption (HTTPS)                              │
│  - API key authentication                                  │
│  - OAuth 2.0 for user authentication                       │
│  - Rate limiting & throttling                              │
│                                                            │
│ Layer 3: Application Security                              │
│  - Spring Security configuration                           │
│  - Authorization (role-based access control)               │
│  - Input validation & sanitization                         │
│  - CSRF protection                                         │
│                                                            │
│ Layer 4: Data Security                                     │
│  - Encryption at rest (database-level encryption)          │
│  - Encryption in transit (TLS)                             │
│  - Sensitive data masking in logs                          │
│  - Database access controls                                │
│                                                            │
│ Layer 5: Audit & Compliance                                │
│  - Comprehensive audit logging                             │
│  - Immutable audit trail                                   │
│  - Compliance reporting (PCI-DSS, SOX)                     │
│  - Regular security assessments                            │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### Compliance Requirements

**Regulatory Standards:**
- **PCI-DSS:** Payment Card Industry Data Security Standard
  - Encryption of cardholder data
  - Access control & authentication
  - Regular security testing
  
- **SOX:** Sarbanes-Oxley (financial reporting)
  - Complete audit trail
  - Segregation of duties
  - Change management

- **GDPR:** General Data Protection Regulation (if applicable)
  - Data privacy
  - Right to be forgotten
  - Data breach notification

### Implementation Examples

**Spring Security Configuration:**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/transactions/**").hasRole("USER")
                .anyRequest().authenticated()
            .and()
            .httpBasic();
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**Audit Logging:**
```java
@Aspect
@Component
public class AuditAspect {
    
    @Around("@annotation(Auditable)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        Object[] args = pjp.getArgs();
        
        auditLogRepository.save(AuditLog.builder()
            .eventType("METHOD_CALL")
            .reference(method)
            .payload("Args: " + Arrays.toString(args))
            .createdAt(LocalDateTime.now())
            .build());
        
        return pjp.proceed();
    }
}
```

---

## Conclusion

This comprehensive migration architecture provides a detailed roadmap for transitioning from a legacy mainframe banking system to a modern, scalable Java Spring Boot platform. The architecture ensures:

1. **Functional Parity:** All mainframe batch and online programs mapped to Spring Boot equivalents
2. **Data Integrity:** Complete migration with validation and reconciliation
3. **Performance:** Optimization strategies for millions of records
4. **Scalability:** Cloud-native design with containerization & Kubernetes
5. **Security:** Multi-layered security architecture with compliance
6. **Maintainability:** Modern Java ecosystem with extensive tooling and documentation

### Next Steps

1. **Stakeholder Review:** Present architecture to business and technical stakeholders
2. **Detailed Design:** Develop detailed design documents for each component
3. **Proof of Concept:** Implement sample batch jobs to validate architecture
4. **Project Planning:** Create detailed project plan with resource allocation
5. **Infrastructure Setup:** Provision development/test environments
6. **Team Training:** Conduct Spring Boot training for development team

---

**Document Version History:**
- v1.0 (2026-01-08): Initial Architecture Document

**Prepared by:** Architecture Team  
**Review Status:** Pending Stakeholder Review  
**Approval Status:** Not Yet Approved

---

*This document is confidential and intended for authorized internal use only.*
