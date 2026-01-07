## Performance Guide - Enterprise Banking Batch Engine

This document describes **how to think about performance** for this Spring Boot / Spring Batch application,
especially when processing **1–10 million records** and when comparing to a **mainframe** workload that has
very fast I/O.

It also summarizes **code-level improvements** and **operational tuning** you can apply.

---

### 1. Mindset: Mainframe vs Spring Batch

Mainframe batch jobs are typically fast because they:

- Run close to the data (e.g. VSAM/DB2 on z/OS)
- Use highly optimized sequential I/O and memory layouts
- Are tuned for a single, well-known workload

Spring Batch can approach similar throughput if you:

- **Stream data** instead of loading whole datasets in memory
- **Write in chunks** using batch inserts / updates
- **Minimize network and serialization overhead**
- **Tune JVM, connection pools, and database** for the target load

The goal is to **reduce per-record overhead** and **maximize sequential, batched I/O**.

---

### 2. Current Design (Summary)

The current project uses:

- **Chunk-oriented processing** (`chunk(100)` for daily/monthly, `chunk(1000)` for file-load)
- **JPA-based reads and writes** (`JpaPagingItemReader` and repositories)
- **`JdbcBatchItemWriter` for file-load** (efficient batch insert into staging table)
- **Metrics and logging** via:
  - `BatchMetricsListener` – logs job start/end and duration, publishes Micrometer metrics
  - `StepTimingListener` – logs step start/end, duration, and read/write/skip counts

These are good starting points, but for 1–10M records you’ll usually need additional tuning.

---

### 3. Logging for Job and Step Timings

You asked specifically to **know how much time each step/job is taking**.

This is implemented in:

- `batch/metrics/BatchMetricsListener` – logs job duration and status:
  - Example log:
    - `Job [dailyTransactionJob] completed with status [COMPLETED] in 5234 ms`
- `batch/metrics/StepTimingListener` – logs step timings:
  - Example log:
    - `Step [dailyTransactionStep] finished with status [COMPLETED] in 4987 ms. ReadCount=1000000, WriteCount=1000000, SkipCount=0`

These listeners are wired into all jobs/steps:

- **Daily job**: `DailyTransactionJobConfig`
- **File-load job**: `FileLoadJobConfig`
- **Monthly interest job**: `MonthlyInterestJobConfig`

You can watch the logs (INFO level) during a run to understand where time is spent.

---

### 4. General Performance Strategies (1M–10M Records)

#### 4.1 Chunk Size Tuning

- **What it is**: Number of items processed per transaction (`chunk(n)`).
- **Trade-off**:
  - Small chunks: more transactions, less memory per chunk, lower latency for commits.
  - Large chunks: fewer transactions, better throughput, but higher rollback cost and memory use.
- **Guidelines**:
  - For daily and monthly jobs, try: `chunk(500)` or `chunk(1000)` instead of `100`.
  - For file-load job, with `JdbcBatchItemWriter`, `chunk(2000–5000)` can give better throughput.

#### 4.2 Use Streaming Readers/Writers

- **Already in place**:
  - `JpaPagingItemReader` streams data page-by-page from the database.
  - `FlatFileItemReader` streams from a file.
  - `JdbcBatchItemWriter` does batched inserts.
- **Further improvements**:
  - For very large tables, consider **partitioning** or **cursor-based readers**:
    - Partition by **ID range** or **account number hash**.
    - Use Spring Batch **partitioned steps** to process multiple partitions in parallel.

#### 4.3 Minimize Object Allocation and Serialization

- Avoid unnecessary DTOs or conversions inside processors/writers.
- Keep audit payloads small and avoid heavy JSON serialization per record.
- Prefer **primitive types** and **simple data structures** inside hot loops.

#### 4.4 Database Indexing and Query Tuning

- Ensure **indexes** match your most frequent WHERE and JOIN clauses:
  - `transaction_staging(processed_flag)` – already indexed.
  - `accounts(account_number)` – unique index via schema.
  - For interest job: `accounts(status)` – already indexed.
- For huge datasets (10M+), consider **table partitioning** at the DB level:
  - Partition on date/time, account ranges, or status.
- Use **EXPLAIN / EXPLAIN ANALYZE** on queries to ensure they use indexes.

#### 4.5 Batch Size and JDBC Settings

- Configure JPA and JDBC batch size for better batching:
  - In `application-postgres.yml` (example):
    ```yaml
    spring:
      jpa:
        properties:
          hibernate:
            jdbc:
              batch_size: 1000
            order_inserts: true
            order_updates: true
    ```
- Ensure the driver and database support batching efficiently (Postgres does).

#### 4.6 Connection Pool Tuning

- Use a reasonable **HikariCP** configuration:
  - `maximumPoolSize`: 10–30 for large batch jobs, depending on CPU and DB capacity.
  - `minimumIdle`: a few connections ready.
- For partitioned or multi-threaded steps, increase pool size to avoid connection starvation.

#### 4.7 JVM and GC Tuning

- For 1–10M records, set **JVM heap** appropriately (e.g. 2–4 GB):
  - `-Xms2g -Xmx2g` (or higher if needed).
- Use a modern GC (G1 is default in recent JVMs):
  - Tune max pause time goals if necessary.

---

### 5. Spring Batch–Specific Optimizations

#### 5.1 Partitioned Steps (Parallelism)

To emulate mainframe-style parallel processing, use **partitioned steps**:

- Example idea for **dailyTransactionJob**:
  - Partition by `id` ranges in `transaction_staging` (e.g. 10 partitions).
  - Each partition runs a step instance with a restricted query:
    - `SELECT t FROM TransactionStaging t WHERE t.processedFlag = false AND t.id BETWEEN :minId AND :maxId`
  - Use a **`Partitioner`** that assigns `minId`/`maxId` per partition.

Benefits:

- Utilizes multi-core CPUs.
- Increases throughput significantly for CPU-bound or I/O-bound workloads.

#### 5.2 Multi-threaded Steps

An alternative (simpler than partitioning) is a **multi-threaded step**:

- Configure a `TaskExecutor` (e.g. `ThreadPoolTaskExecutor`) and attach it to the step.
- Spring Batch will process chunks in parallel threads.

Use this carefully:

- Ensure thread-safety in processors/writers.
- Avoid shared mutable state without synchronization.

#### 5.3 Reduce Commit Frequency for Simple Operations

For the file-load job, if DB can handle it, increasing chunk size reduces commit overhead.

Trade-off:

- Larger chunk size => fewer commits => higher throughput.
- But, a rollback will reprocess more records.

Start with `chunk(1000)` and benchmark before increasing further.

---

### 6. Comparing to Mainframe I/O

Mainframe I/O is often **sequential and close to disk**, with low overhead per record.

To get closer with Spring Batch:

- **Avoid random access** patterns; stick to sequential scans where possible.
- For file processing:
  - Use `FlatFileItemReader` with **streaming** (already in place).
  - Keep lines simple and avoid heavy parsing logic inside the step.
- For database processing:
  - Use **streaming result sets** and paging readers (like `JpaPagingItemReader`).
  - For extreme scale, consider **native SQL + `JdbcCursorItemReader`** with forward-only cursors.

---

### 7. Measuring and Observing Performance

#### 7.1 Logs

Watch the logs for lines from:

- `BatchMetricsListener`:
  - `Job [fileLoadJob] completed with status [COMPLETED] in 120000 ms`
- `StepTimingListener`:
  - `Step [fileLoadStep] finished with status [COMPLETED] in 118500 ms. ReadCount=1000000, WriteCount=1000000, SkipCount=10`

These give you **job-level and step-level timings** out of the box.

#### 7.2 Prometheus Metrics

Expose metrics via `/actuator/prometheus`:

- `batch_job_starts_total`
- `batch_job_completions_total`
- `batch_job_duration_seconds` (if you map the Timer properly)

You can scrape these metrics and build **Grafana dashboards** similar to mainframe performance monitors.

---

### 8. Concrete Tuning Checklist (1M–10M Records)

Use this as a quick **checklist** when you move from small to very large datasets:

- **Chunk size**:
  - Daily job: start with `chunk(500)` or `chunk(1000)`.
  - File-load job: start with `chunk(2000)`.
- **Database**:
  - Ensure indexes on `processed_flag`, `account_number`, and `status`.
  - Analyze queries with `EXPLAIN ANALYZE`.
  - Consider table partitioning for very large tables.
- **JPA / JDBC**:
  - Enable batching (`hibernate.jdbc.batch_size=500` or `1000`).
  - Turn on `order_inserts` / `order_updates`.
- **Parallelism**:
  - Add partitioned steps or multi-threaded steps where safe.
  - Increase HikariCP `maximumPoolSize` accordingly (e.g. 20–30).
- **JVM**:
  - Increase heap (`-Xms2g -Xmx2g` or higher, depending on size).
  - Monitor GC pauses with `-Xlog:gc*` (JDK 11+ syntax) or similar flags.
- **Monitoring**:
  - Use `StepTimingListener` logs to identify slow steps.
  - Use Prometheus metrics to visualize job durations and throughput over time.

---

### 9. Future Enhancements

If you need to push performance even further (closer to mainframe throughput):

- Switch some critical paths from **JPA** to **plain JDBC** with tuned SQL.
- Introduce **asynchronous writer patterns** for downstream systems (e.g. publish to Kafka instead of DB writes).
- Use **Spring Cloud Task / Data Flow** to orchestrate and scale multiple batch workers.
- Consider **native images** (GraalVM) for lower startup overhead (though long-running batch jobs care more about steady-state).

---

### 10. Summary

- This project already uses **chunk-oriented processing, streaming readers, and batch writers**, which are the foundation for high throughput batch processing.
- Added **job and step timing loggers** give you visibility into where time is spent.
- To handle **1M–10M records**, focus on **chunk size, partitioning, JDBC batching, DB indexing, connection pooling, and JVM tuning**.
- While mainframes have advantages in raw I/O, a well-tuned Spring Batch application on modern hardware can reach very competitive throughput for enterprise batch workloads.


