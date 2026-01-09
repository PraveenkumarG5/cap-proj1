# Architecture — Enterprise Banking Batch Engine

This document explains the high-level architecture, patterns, and how components interact.

## Modules
- `backend` — Spring Boot application exposing REST endpoints and Spring Batch jobs.
- `frontend` — Vite + React app used for simple dashboard and job controls.

## High-level architecture
- The system is modular with a backend (Java) and frontend (React).
- `backend` uses Spring Boot and Spring Batch to implement the ETL-like batch jobs.
- Jobs are triggered via REST endpoints (`JobController`) and can be triggered manually or scheduled by orchestration.
- Data model resides in relational DB (H2 for local tests, Postgres for production).

## Layering
- `web` layer: REST controllers (e.g., `JobController`, `DashboardController`).
- `batch` layer: Spring Batch job definitions (file loader, daily processing, monthly interest).
- `domain` layer: JPA entities (`Account`, `TransactionStaging`, `AuditLog`).
- `repository` layer: Spring Data JPA repositories providing persistence operations.
- `config` layer: application configuration and beans.

## Design & Architectural Patterns
- Repository Pattern: Spring Data JPA repositories encapsulate DB access (`AccountRepository`, etc.).
- Builder Pattern: Lombok `@Builder` used on domain objects for readable construction.
- Listener Pattern: Batch listeners (`StepTimingListener`, `BatchMetricsListener`, JobExecutionListener`) observe lifecycle events.
- Template Method / Strategy: Spring Batch `ItemReader` / `ItemProcessor` / `ItemWriter` form a processing pipeline; behavior supplied via lambdas or beans.
- Factory / Fluent Builder: `JobBuilder` and `StepBuilder` create configured Job/Step instances using fluent APIs.
- Layered Architecture: clear separation of concerns (web, batch, domain, repository).

## Integration points
- Micrometer: metrics are published to Prometheus (via `micrometer-registry-prometheus`).
- Database: H2 (local) or Postgres (production). SQL schema in `src/main/resources/schema.sql`.
- Frontend: built assets are produced by Vite and (optionally) copied/included by backend build.

## Runtime flow (File Load job)
1. User uploads or places CSV in `backend/inbound/transactions.csv`.
2. Trigger via `POST /api/jobs/load-file?path=...`.
3. Spring Batch `fileLoadJob` runs a `FlatFileItemReader` → `ItemProcessor` → `JdbcBatchItemWriter` pipeline.
4. Records are staged in `transaction_staging` and later processed by the daily job.
5. Spring Batch metadata tables track job lifecycle and execution history.

## Key classes/files
- `backend/src/main/java/com/example/bankingbatch/batch/file/FileLoadJobConfig.java` — file-load job configuration.
- `backend/src/main/java/com/example/bankingbatch/batch/daily/DailyTransactionJobConfig.java` — daily processing job.
- `backend/src/main/java/com/example/bankingbatch/batch/metrics/BatchMetricsListener.java` — metrics listener.
- `backend/src/main/java/com/example/bankingbatch/repository` — Spring Data repositories.

## Operational considerations
- Jobs are idempotent when possible; ensure CSV ingestion handles duplicates (unique constraint on `txn_id`).
- For scale, use partitioning and multiple DB connections.
- Use health checks and metrics to detect slowdowns.

## Extensibility
- New job definitions follow existing pattern: define `Step` and `Job` beans, add listeners and metrics.
- Repositories and entities are straightforward to add with Spring Data JPA + Lombok.

## Diagram (textual)
[Client] -> (REST) -> [JobController] -> [Spring Batch Jobs]
Spring Batch Jobs -> [Steps: Reader -> Processor -> Writer] -> [Database]
Metrics emitted -> [Micrometer/Prometheus] -> [Grafana]

---

For any architectural decision or to add diagrams, I can generate a PlantUML or Mermaid diagram next—tell me which format you prefer.