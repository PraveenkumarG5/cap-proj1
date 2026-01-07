## Enterprise Banking Batch Engine (Modular)

Multi-module Maven project with a Spring Boot 3.2 backend (Spring Batch) and a Vite + React frontend dashboard.

### Modules

- **root**: Aggregator (`enterprise-banking-batch-engine-modular`)
- **backend**: Spring Boot batch engine (`backend`)
- **frontend**: Vite + React SPA (`frontend`)

### Build

- **Full build (backend + frontend)**:

```bash
mvn clean package
```

- **Backend only** (from `backend`):

```bash
mvn clean package
```

The `frontend-maven-plugin` builds the SPA and the backend `pom.xml` copies `frontend/dist` into `backend/target/classes/static`.

### Run

- From project root, run:

```bash
mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=h2
```

Or for Postgres (adjust URL/credentials in `application-postgres.yml`):

```bash
mvn -pl backend spring-boot:run -Dspring-boot.run.profiles=postgres
```

### Database Profiles

- **H2 in-memory**:
  - Config: `backend/src/main/resources/application-h2.yml`
  - H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:bankdb`)

- **Postgres**:
  - Config: `backend/src/main/resources/application-postgres.yml`
  - Update `spring.datasource.url`, `username`, `password` as needed.

### REST Endpoints

- **Job triggers**
  - `POST /api/jobs/daily` – run Daily Transaction Processing Job
  - `POST /api/jobs/interest` – run Monthly Interest Job
  - `POST /api/jobs/load-file?path=...` – run File-Load Job (CSV)

- **Dashboard APIs**
  - `GET /api/dashboard/instances` – job instances with latest execution status
  - `GET /api/dashboard/job-run-logs` – list of `JobRunLog` rows

### Frontend (Vite + React)

- Dev server (optional, during development; runs on 5173 and proxies `/api` to backend):

```bash
cd frontend
npm install
npm run dev
```

- Production build is invoked automatically by Maven during `mvn clean package` using `frontend-maven-plugin`.

### Helper Script: Generate Transactions CSV (Configurable, 1M+)

- Script: `backend/generate-transactions.js`
- Generates a large CSV at `backend/inbound/transactions.csv`.
- Row count configurable via CLI arg or `COUNT` env (defaults to **1,000,000**).

Usage (from `backend`):

```bash
# default 1,000,000 rows
node generate-transactions.js

# 2,000,000 rows
node generate-transactions.js 2000000

# 500,000 rows via env
COUNT=500000 node generate-transactions.js
```

Then trigger file-load job:

```bash
curl -X POST "http://localhost:8080/api/jobs/load-file?path=backend/inbound/transactions.csv"
```

### How to Test Each Job (JUnit / Integration)

All tests live under `backend/src/test/java/com/example/bankingbatch/batch` and use `@SpringBatchTest` with the `h2` profile.

- **Run all tests**
  ```bash
  mvn test
  ```

- **Daily Transaction Job**
  - Test: `DailyTransactionJobIntegrationTest`
  - What it checks: inserts a staging row, runs `dailyTransactionJob`, asserts `COMPLETED`, verifies account balance increased and audit log written.

- **Monthly Interest Job**
  - Test: `MonthlyInterestJobIntegrationTest`
  - What it checks: runs `monthlyInterestJob`, asserts `COMPLETED`, verifies balance increased by at least 5% and audit log written.

- **File-Load Job**
  - Test: `FileLoadJobIntegrationTest`
  - What it checks: generates a temp CSV, runs `fileLoadJob` with `filePath` parameter, asserts `COMPLETED`, verifies staging rows created.

To run a single test class:

```bash
mvn -pl backend -Dtest=DailyTransactionJobIntegrationTest test
```

### Notes

- Java 17, Spring Boot 3.2.x (BOM-managed dependencies).
- Spring Batch schema is auto-initialized, JPA uses SQL from `schema.sql` and data from `data.sql` (now seeds 50 accounts).
- Actuator + Prometheus (`/actuator/prometheus`) are exposed via configuration.


