# Performance Guide — Enterprise Banking Batch Engine

This guide expands the existing performance notes and gives step-by-step recommendations for running and tuning the batch jobs for someone newer to the project.

## Goals
- Process very large CSV files (hundreds of thousands → millions of rows).
- Keep job runtime and resource use reasonable.
- Make safe, incremental tuning changes.

## Quick start — generate test data
From the `backend` folder you can generate a large CSV used by the file-load job:

```bash
# generate default 1,000,000 rows (from backend folder)
node generate-transactions.js

# specify row count
node generate-transactions.js 2000000
```

The script writes `backend/inbound/transactions.csv` by default.

## Run the app locally (H2 profile)
The easiest way to run locally uses the in-memory H2 profile:

```powershell
# set Java 17 for session
$env:JAVA_HOME='C:\Users\prave\.jdk\jdk-17.0.16(2)'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd backend
mvn -Dspring-boot.run.profiles=h2 spring-boot:run
```

Trigger the file-load job (example):

```bash
curl -X POST "http://localhost:8080/api/jobs/load-file?path=backend/inbound/transactions.csv"
```

## Observability and metrics
- Micrometer is configured with Prometheus registry. Metrics emitted:
  - `batch.job.starts` — counter when jobs start
  - `batch.job.completions` — counter when jobs finish (tagged by job, status)
  - `batch.job.duration` — timer for job durations
- Tail logs for `StepTimingListener` and `BatchMetricsListener` messages to see per-step runtime.

## Tuning knobs
1. Chunk size: change the chunk size in `FileLoadJobConfig.fileLoadStep(...)` (currently `chunk(1000)`). Larger chunk improves throughput up to memory/DB limits.
2. Commit frequency: chunk size controls commit frequency — balance between transaction overhead and rollbacks.
3. Parallelism / Partitioning:
   - For heavy workloads, use partitioned steps to split ranges of `transaction_staging.id` across worker threads/processes.
   - Partitioning reduces end-to-end latency when multiple cores/DB connections available.
4. JDBC batching and connection pool:
   - Use `JdbcBatchItemWriter` (already used) and ensure your DB connection pool has enough connections (HikariCP `maximumPoolSize`) to support parallelism.
5. Database tuning:
   - Add indexes on columns used for queries (e.g., `processed_flag`, `account_number`) if you run large selects/updates.
   - Tune `wal`, `shared_buffers`, and `checkpoint` for Postgres to match workload.
6. JVM and GC:
   - Use a recent JDK (17+); tune heap (`-Xms -Xmx`) to avoid frequent GC.
   - For throughput, use G1 or ZGC depending on latency vs throughput needs. Example for throughput:
     `-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200`

## Recommended testing workflow
1. Start with H2 and small CSV (10k rows) to validate functional correctness.
2. Increase to 100k–1M rows and monitor memory, CPU, DB I/O.
3. Try different chunk sizes: 500, 1000, 5000 and measure throughput.
4. If CPU is underutilized and DB can handle more connections, enable partitioning into N partitions (N ~= #cores).

## Partitioning example (high level)
- Implement a `Partitioner` that computes `minId` & `maxId` ranges for each partition and creates `ExecutionContext` for steps.
- Configure a `TaskExecutor` with a thread pool to run partitions in parallel.

## Safety tips
- Always test tuning changes on a staging environment with similar DB size.
- Watch for deadlocks when using many concurrent writers.
- Use smaller chunks if you see high rollback costs.

## Troubleshooting
- Slow inserts: ensure `JdbcBatchItemWriter` batching is used, and DB has appropriate indexes.
- OutOfMemory: reduce chunk size or increase heap.
- Long GC pauses: tune GC settings or switch collector.

## Useful commands
```powershell
# build backend and frontend (from repository root)
$env:JAVA_HOME='C:\Users\prave\.jdk\jdk-17.0.16(2)'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd backend
mvn -DskipTests clean package
cd ..\frontend
mvn -DskipTests clean package
```

## Next steps
- Consider adding production-grade monitoring (Prometheus + Grafana dashboards).
- Create an automated profiling job that runs job on representative data and records throughput numbers for different chunk sizes and partition counts.
