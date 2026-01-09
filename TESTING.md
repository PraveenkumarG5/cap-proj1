# Testing Guide - Enterprise Banking Batch Engine

This document provides comprehensive instructions for testing all components of the Enterprise Banking Batch Engine application. It covers automated tests (JUnit/Integration), manual testing procedures, API testing, and performance testing scenarios.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Running Automated Tests](#running-automated-tests)
3. [Testing Daily Transaction Processing Job](#testing-daily-transaction-processing-job)
4. [Testing Monthly Interest Job](#testing-monthly-interest-job)
5. [Testing File-Load Job (CSV)](#testing-file-load-job-csv)
6. [Manual API Testing](#manual-api-testing)
7. [Frontend Testing](#frontend-testing)
8. [Database Testing (H2 vs Postgres)](#database-testing-h2-vs-postgres)
9. [Performance Testing with Large Datasets](#performance-testing-with-large-datasets)
10. [Troubleshooting Common Issues](#troubleshooting-common-issues)

---

## Prerequisites

Before running tests, ensure you have:

- **Java 17** installed and configured
- **Maven 3.6+** installed
- **Node.js 18+** (for generating test CSV files)
- **PostgreSQL** (optional, for Postgres profile testing)
- **IntelliJ IDEA** or any IDE with Maven support (optional but recommended)

### Verify Prerequisites

```bash
# Check Java version
java -version  # Should show Java 17

# Check Maven version
mvn -version   # Should show Maven 3.6+

# Check Node.js version
node -v        # Should show Node 18+
```

---

## Running Automated Tests

### Run All Tests

Execute all test suites from the project root:

```bash
mvn clean test
```

Or from the backend module:

```bash
mvn -pl backend clean test
```

**Expected Output:**
- All three integration tests should pass
- Test results summary showing `Tests run: 3, Failures: 0, Errors: 0`

### Run Individual Test Classes

#### Daily Transaction Job Test

```bash
mvn -pl backend -Dtest=DailyTransactionJobIntegrationTest test
```

**What This Test Does:**
1. Loads an existing account (`ACC-1001`) from the seeded data
2. Creates a new `TransactionStaging` row with a CREDIT transaction of $100.00
3. Launches the `dailyTransactionJob`
4. Verifies:
   - Job completes with `COMPLETED` exit status
   - Account balance increased by exactly $100.00
   - Audit log entries were created

#### Monthly Interest Job Test

```bash
mvn -pl backend -Dtest=MonthlyInterestJobIntegrationTest test
```

**What This Test Does:**
1. Loads an existing account (`ACC-1001`) and captures its original balance
2. Launches the `monthlyInterestJob`
3. Verifies:
   - Job completes with `COMPLETED` exit status
   - Account balance increased by at least 5% (interest rate)
   - Audit log entries were created for interest calculation

#### File-Load Job Test

```bash
mvn -pl backend -Dtest=FileLoadJobIntegrationTest test
```

**What This Test Does:**
1. Creates a temporary CSV file with sample transaction data
2. Launches the `fileLoadJob` with the CSV file path as a parameter
3. Verifies:
   - Job completes with `COMPLETED` exit status
   - Transaction staging table contains the loaded rows

### Run Tests with Specific Profile

To run tests against a specific database profile:

```bash
# H2 (default, in-memory)
mvn -pl backend -Dtest=DailyTransactionJobIntegrationTest test

# Postgres (requires running Postgres instance)
mvn -pl backend -Dtest=DailyTransactionJobIntegrationTest test -Dspring.profiles.active=postgres
```

---

## Testing Daily Transaction Processing Job

### Overview

The Daily Transaction Processing Job reads unprocessed transactions from `transaction_staging`, applies CREDIT/DEBIT operations to accounts, writes audit logs, and marks transactions as processed.

### Automated Test Details

**Test Class:** `DailyTransactionJobIntegrationTest`

**Test Method:** `dailyTransactionJobProcessesTransactions()`

**Step-by-Step Test Flow:**

1. **Setup Phase:**
   ```java
   // Load account ACC-1001 (seeded in data.sql)
   Account account = accountRepository.findByAccountNumber("ACC-1001").orElseThrow();
   BigDecimal originalBalance = account.getBalance(); // e.g., 1000.00
   ```

2. **Create Test Data:**
   ```java
   // Insert a staging transaction
   TransactionStaging staging = TransactionStaging.builder()
       .txnId("TEST-TXN-1")
       .accountNumber("ACC-1001")
       .amount(new BigDecimal("100.00"))
       .direction("CREDIT")
       .processedFlag(false)  // Must be false to be picked up by reader
       .build();
   stagingRepository.save(staging);
   ```

3. **Launch Job:**
   ```java
   jobLauncherTestUtils.setJob(dailyTransactionJob);
   JobExecution execution = jobLauncherTestUtils.launchJob(
       new JobParametersBuilder()
           .addLong("timestamp", System.currentTimeMillis())
           .toJobParameters()
   );
   ```

4. **Assertions:**
   - Exit status is `COMPLETED`
   - Account balance increased by exactly $100.00
   - Audit log contains entries

### Manual Testing via API

1. **Start the application:**
   ```bash
   mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2
   ```

2. **Prepare test data** (using H2 console or SQL):
   - Open H2 console: `http://localhost:8080/h2-console`
   - JDBC URL: `jdbc:h2:mem:bankdb`
   - Insert a staging transaction:
     ```sql
     INSERT INTO transaction_staging (txn_id, account_number, amount, direction, processed_flag, created_at)
     VALUES ('MANUAL-TXN-1', 'ACC-1001', 250.00, 'CREDIT', false, CURRENT_TIMESTAMP);
     ```

3. **Trigger the job:**
   ```bash
   curl -X POST http://localhost:8080/api/jobs/daily
   ```

4. **Verify results:**
   ```bash
   # Check account balance
   curl http://localhost:8080/api/dashboard/instances
   
   # Check audit logs (via H2 console)
   SELECT * FROM audit_log WHERE reference LIKE '%ACC-1001%';
   ```

### Testing Edge Cases

#### Test 1: Insufficient Funds (DEBIT exceeding balance)

1. Set account balance to $50.00
2. Insert staging transaction: DEBIT $100.00
3. Run job
4. **Expected:** Job completes, transaction is skipped (fault tolerance), audit log shows error

#### Test 2: Invalid Account Number

1. Insert staging transaction with non-existent account: `ACC-9999`
2. Run job
3. **Expected:** Job completes, transaction skipped, no account update

#### Test 3: Multiple Transactions for Same Account

1. Insert 5 CREDIT transactions of $50.00 each for `ACC-1001`
2. Run job
3. **Expected:** All processed, balance increased by $250.00 total

---

## Testing Monthly Interest Job

### Overview

The Monthly Interest Job applies a 5% interest rate to all ACTIVE accounts, updates balances, and creates audit log entries.

### Automated Test Details

**Test Class:** `MonthlyInterestJobIntegrationTest`

**Test Method:** `monthlyInterestJobAppliesInterest()`

**Step-by-Step Test Flow:**

1. **Setup Phase:**
   ```java
   Account account = accountRepository.findByAccountNumber("ACC-1001").orElseThrow();
   BigDecimal originalBalance = account.getBalance(); // e.g., 1000.00
   ```

2. **Launch Job:**
   ```java
   jobLauncherTestUtils.setJob(monthlyInterestJob);
   JobExecution execution = jobLauncherTestUtils.launchJob(
       new JobParametersBuilder()
           .addLong("timestamp", System.currentTimeMillis())
           .toJobParameters()
   );
   ```

3. **Assertions:**
   - Exit status is `COMPLETED`
   - New balance ≥ original × 1.05 (5% interest)
   - Audit log contains entries for each account

### Manual Testing via API

1. **Start the application:**
   ```bash
   mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2
   ```

2. **Check initial balances** (via H2 console):
   ```sql
   SELECT account_number, balance, status FROM accounts WHERE status = 'ACTIVE';
   ```

3. **Trigger the job:**
   ```bash
   curl -X POST http://localhost:8080/api/jobs/interest
   ```

4. **Verify results:**
   ```sql
   -- Check updated balances (should be 5% higher)
   SELECT account_number, balance FROM accounts WHERE status = 'ACTIVE';
   
   -- Check audit logs
   SELECT * FROM audit_log WHERE event_type LIKE '%INTEREST%';
   ```

### Testing Edge Cases

#### Test 1: Only ACTIVE Accounts Receive Interest

1. Ensure some accounts have status `INACTIVE` or `SUSPENDED`
2. Run job
3. **Expected:** Only ACTIVE accounts get interest, others unchanged

#### Test 2: Zero Balance Accounts

1. Set an account balance to $0.00
2. Run job
3. **Expected:** Balance remains $0.00 (0 × 1.05 = 0)

#### Test 3: Large Balance Accounts

1. Set an account balance to $1,000,000.00
2. Run job
3. **Expected:** New balance = $1,050,000.00 (exactly 5% increase)

---

## Testing File-Load Job (CSV)

### Overview

The File-Load Job reads CSV files containing transaction data, validates rows, and inserts them into the `transaction_staging` table. It handles malformed rows gracefully using fault tolerance.

### Automated Test Details

**Test Class:** `FileLoadJobIntegrationTest`

**Test Method:** `fileLoadJobLoadsCsv()`

**Step-by-Step Test Flow:**

1. **Create Temporary CSV:**
   ```java
   Path tempFile = Files.createTempFile("transactions", ".csv");
   try (FileWriter writer = new FileWriter(tempFile.toFile())) {
       writer.write("txnId,accountNumber,amount,direction\n");
       writer.write("CSV-TXN-1,ACC-1001,50.00,CREDIT\n");
   }
   ```

2. **Launch Job with File Path:**
   ```java
   jobLauncherTestUtils.setJob(fileLoadJob);
   JobExecution execution = jobLauncherTestUtils.launchJob(
       new JobParametersBuilder()
           .addLong("timestamp", System.currentTimeMillis())
           .addString("filePath", tempFile.toAbsolutePath().toString())
           .toJobParameters()
   );
   ```

3. **Assertions:**
   - Exit status is `COMPLETED`
   - Staging repository contains the loaded rows

### Manual Testing via API

1. **Generate a test CSV file:**
   ```bash
   cd backend
   node generate-transactions.js 1000  # Generate 1000 rows
   ```

   This creates `backend/inbound/transactions.csv` with format:
   ```csv
   txnId,accountNumber,amount,direction
   TXN-0000001,ACC-1001,150.50,CREDIT
   TXN-0000002,ACC-1002,75.25,DEBIT
   ...
   ```

2. **Start the application:**
   ```bash
   mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2
   ```

3. **Trigger the file-load job:**
   ```bash
   curl -X POST "http://localhost:8080/api/jobs/load-file?path=backend/inbound/transactions.csv"
   ```

4. **Verify results:**
   ```sql
   -- Check loaded rows
   SELECT COUNT(*) FROM transaction_staging;
   
   -- Check specific rows
   SELECT * FROM transaction_staging WHERE processed_flag = false LIMIT 10;
   ```

### Testing Edge Cases

#### Test 1: Malformed CSV Rows

Create a CSV with invalid data:
```csv
txnId,accountNumber,amount,direction
VALID-TXN-1,ACC-1001,100.00,CREDIT
INVALID-TXN-2,,,  # Missing fields
INVALID-TXN-3,ACC-1001,NOT_A_NUMBER,CREDIT  # Invalid amount
VALID-TXN-4,ACC-1002,200.00,DEBIT
```

**Expected:** Job completes, valid rows loaded, invalid rows skipped (fault tolerance)

#### Test 2: Large CSV File (1M+ rows)

```bash
# Generate 1 million rows
cd backend
node generate-transactions.js 1000000

# Trigger job
curl -X POST "http://localhost:8080/api/jobs/load-file?path=backend/inbound/transactions.csv"
```

**Expected:** Job completes successfully, all rows loaded (may take several minutes)

#### Test 3: Non-existent File Path

```bash
curl -X POST "http://localhost:8080/api/jobs/load-file?path=/nonexistent/file.csv"
```

**Expected:** Job fails with appropriate error message

#### Test 4: Duplicate Transaction IDs

Create CSV with duplicate `txnId`:
```csv
txnId,accountNumber,amount,direction
DUPLICATE-1,ACC-1001,100.00,CREDIT
DUPLICATE-1,ACC-1002,200.00,DEBIT
```

**Expected:** First row inserted, second row fails due to unique constraint (if constraint exists)

---

## Manual API Testing

### Using cURL

#### 1. Trigger Daily Transaction Job

```bash
curl -X POST http://localhost:8080/api/jobs/daily
```

**Response:**
```json
{
  "jobInstanceId": 1,
  "jobExecutionId": 1,
  "status": "STARTED"
}
```

#### 2. Trigger Monthly Interest Job

```bash
curl -X POST http://localhost:8080/api/jobs/interest
```

#### 3. Trigger File-Load Job

```bash
curl -X POST "http://localhost:8080/api/jobs/load-file?path=backend/inbound/transactions.csv"
```

#### 4. Get Job Instances Dashboard

```bash
curl http://localhost:8080/api/dashboard/instances
```

**Response:**
```json
[
  {
    "jobName": "dailyTransactionJob",
    "instanceId": 1,
    "status": "COMPLETED",
    "startTime": "2024-01-15T10:30:00",
    "endTime": "2024-01-15T10:30:05",
    "recordsProcessed": 500
  }
]
```

### Using Postman

1. **Import Collection:**
   - Create a new collection: "Banking Batch Engine"
   - Add requests for each endpoint

2. **Environment Variables:**
   - `baseUrl`: `http://localhost:8080`

3. **Test Requests:**
   - `POST {{baseUrl}}/api/jobs/daily`
   - `POST {{baseUrl}}/api/jobs/interest`
   - `POST {{baseUrl}}/api/jobs/load-file?path=backend/inbound/transactions.csv`
   - `GET {{baseUrl}}/api/dashboard/instances`

---

## Frontend Testing

### Development Mode Testing

1. **Start backend:**
   ```bash
   mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2
   ```

2. **Start frontend dev server:**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

3. **Access frontend:**
   - Open `http://localhost:5173` in browser
   - Frontend proxies `/api` requests to backend on port 8080

4. **Test UI Components:**
   - Click "Run Daily Transactions" button
   - Click "Run Monthly Interest" button
   - Click "Load Transactions from CSV" button
   - Click "Refresh Dashboard" to see updated job instances and logs

### Production Build Testing

1. **Build full project:**
   ```bash
   mvn clean package
   ```

2. **Start backend (includes frontend static assets):**
   ```bash
   mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2
   ```

3. **Access application:**
   - Open `http://localhost:8080` in browser
   - Frontend is served from `/static` directory

---

## Database Testing (H2 vs Postgres)

### H2 In-Memory Database (Default)

**Profile:** `h2`

**Configuration:** `backend/src/main/resources/application-h2.yml`

**Testing Steps:**

1. **Start with H2:**
   ```bash
   mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2
   ```

2. **Access H2 Console:**
   - URL: `http://localhost:8080/h2-console`
   - JDBC URL: `jdbc:h2:mem:bankdb`
   - Username: `sa`
   - Password: (leave empty)

3. **Verify Data:**
   ```sql
   -- Check seeded accounts (50 accounts)
   SELECT COUNT(*) FROM accounts;
   
   -- Check batch metadata tables
   SELECT * FROM batch_job_instance;
   SELECT * FROM batch_job_execution;
   ```

**Pros:**
- Fast startup
- No external dependencies
- Perfect for unit/integration tests

**Cons:**
- Data lost on application restart
- Not suitable for production

### PostgreSQL Database

**Profile:** `postgres`

**Configuration:** `backend/src/main/resources/application-postgres.yml`

**Prerequisites:**
- PostgreSQL installed and running
- Database `bankdb` created
- User `bankuser` with password `bankpass` (or update config)

**Setup PostgreSQL:**

```sql
-- Create database
CREATE DATABASE bankdb;

-- Create user (adjust password as needed)
CREATE USER bankuser WITH PASSWORD 'bankpass';
GRANT ALL PRIVILEGES ON DATABASE bankdb TO bankuser;
```

**Testing Steps:**

1. **Update configuration** (if needed):
   ```yaml
   # backend/src/main/resources/application-postgres.yml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/bankdb
       username: bankuser
       password: bankpass
   ```

2. **Start with Postgres:**
   ```bash
   mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=postgres
   ```

3. **Verify Data:**
   ```sql
   -- Connect to PostgreSQL
   psql -U bankuser -d bankdb
   
   -- Check tables
   \dt
   
   -- Check accounts
   SELECT COUNT(*) FROM accounts;
   ```

**Pros:**
- Production-ready
- Persistent data
- Better for performance testing

**Cons:**
- Requires external database setup
- Slower startup

---

## Performance Testing with Large Datasets

### Generate Large Transaction CSV

```bash
cd backend

# Generate 1 million rows (default)
node generate-transactions.js

# Generate 2 million rows
node generate-transactions.js 2000000

# Generate 5 million rows
node generate-transactions.js 5000000
```

### Test File-Load Job Performance

1. **Generate large CSV:**
   ```bash
   cd backend
   node generate-transactions.js 1000000
   ```

2. **Start application with monitoring:**
   ```bash
   mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2
   ```

3. **Monitor metrics** (in another terminal):
   ```bash
   # Check Prometheus metrics
   curl http://localhost:8080/actuator/prometheus | grep batch
   ```

4. **Trigger file-load job:**
   ```bash
   curl -X POST "http://localhost:8080/api/jobs/load-file?path=backend/inbound/transactions.csv"
   ```

5. **Monitor job execution:**
   ```bash
   # Check job status
   curl http://localhost:8080/api/dashboard/instances
   ```

### Expected Performance Metrics

- **1M rows:** ~2-5 minutes (depending on hardware)
- **Chunk size:** 1000 rows per chunk
- **Throughput:** ~300-500 rows/second

### Monitor Batch Metrics

Access Prometheus metrics endpoint:

```bash
curl http://localhost:8080/actuator/prometheus
```

Look for:
- `batch_job_starts_total` - Total job starts
- `batch_job_completions_total` - Total job completions
- `batch_job_duration_seconds` - Job execution duration

---

## Troubleshooting Common Issues

### Issue 1: Tests Fail with "Table not found"

**Symptoms:**
```
org.h2.jdbc.JdbcSQLSyntaxErrorException: Table "ACCOUNTS" not found
```

**Solution:**
- Ensure `schema.sql` is in `src/main/resources`
- Check that `spring.sql.init.mode=always` is set in `application-h2.yml`
- Verify `@SpringBootTest` loads the application context correctly

### Issue 2: Job Fails with "Job instance already exists"

**Symptoms:**
```
JobInstanceAlreadyCompleteException: A job instance already exists
```

**Solution:**
- Add unique job parameters (e.g., timestamp) for each run:
  ```java
  new JobParametersBuilder()
      .addLong("timestamp", System.currentTimeMillis())
      .toJobParameters()
  ```

### Issue 3: CSV File Not Found

**Symptoms:**
```
java.io.FileNotFoundException: backend/inbound/transactions.csv
```

**Solution:**
- Ensure CSV file exists at the specified path
- Use absolute path if relative path doesn't work:
  ```bash
  curl -X POST "http://localhost:8080/api/jobs/load-file?path=/absolute/path/to/transactions.csv"
  ```

### Issue 4: Frontend Not Loading

**Symptoms:**
- Blank page or 404 when accessing `http://localhost:8080`

**Solution:**
- Ensure frontend is built: `mvn clean package`
- Check that `frontend/dist` exists and contains `index.html`
- Verify backend copies frontend assets to `target/classes/static`

### Issue 5: Postgres Connection Failed

**Symptoms:**
```
org.postgresql.util.PSQLException: Connection refused
```

**Solution:**
- Verify PostgreSQL is running: `pg_isready`
- Check connection details in `application-postgres.yml`
- Ensure database and user exist:
  ```sql
  CREATE DATABASE bankdb;
  CREATE USER bankuser WITH PASSWORD 'bankpass';
  ```

### Issue 6: Insufficient Memory for Large CSV

**Symptoms:**
```
OutOfMemoryError: Java heap space
```

**Solution:**
- Increase JVM heap size:
  ```bash
  export MAVEN_OPTS="-Xmx2g"
  mvn -pl backend spring-boot:run
  ```
- Or set in `pom.xml`:
  ```xml
  <plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
      <jvmArguments>-Xmx2g</jvmArguments>
    </configuration>
  </plugin>
  ```

---

## Test Coverage Summary

### Current Test Coverage

- ✅ **Daily Transaction Job:** Integration test covers successful processing
- ✅ **Monthly Interest Job:** Integration test covers interest calculation
- ✅ **File-Load Job:** Integration test covers CSV loading

### Areas for Additional Testing (Future Enhancements)

- Unit tests for processors and writers
- Unit tests for REST controllers
- Unit tests for repositories
- Error handling and fault tolerance scenarios
- Concurrent job execution tests
- Database transaction rollback tests

---

## Quick Reference Commands

```bash
# Run all tests
mvn clean test

# Run specific test
mvn -pl backend -Dtest=DailyTransactionJobIntegrationTest test

# Start application (H2)
mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2

# Start application (Postgres)
mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=postgres

# Generate test CSV (1M rows)
cd backend && node generate-transactions.js

# Trigger daily job
curl -X POST http://localhost:8080/api/jobs/daily

# Trigger interest job
curl -X POST http://localhost:8080/api/jobs/interest

# Trigger file-load job
curl -X POST "http://localhost:8080/api/jobs/load-file?path=backend/inbound/transactions.csv"

# Check job instances
curl http://localhost:8080/api/dashboard/instances

# Access H2 console
# Browser: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:bankdb

# Check Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

---

## Conclusion

This testing guide provides comprehensive instructions for testing all aspects of the Enterprise Banking Batch Engine. Follow the procedures above to ensure all components work correctly before deploying to production.

For questions or issues, refer to the main [README.md](README.md) or check the application logs for detailed error messages.

