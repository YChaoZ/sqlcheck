# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project does

A Spring Boot 2.4.11 CLI tool that scans local SQL and Apollo config files, writes HTML/JSON reports, and aggregates SQL scripts when all SQL checks pass (no ERRORs).

## How it runs

- `SqlCheckApplication` starts Spring Boot; `SqlCheckRunner` calls `SqlCheckService.run()` on startup.
- `SqlCheckService` orchestrates: scan `sql/` → scan `apollo/` → generate reports → aggregate if clean.
- Output directories are **auto-derived from `sql-dir`'s parent** (configured via `sqlcheck.sql-dir`):
  - Reports: `<parent>/report/`
  - Aggregation: `<parent>/aggregation/`
- Both output directories are **wiped clean** at the start of every run.

## Common commands

- Run all tests: `mvn test`
- Run one test class: `mvn -Dtest=SqlCheckerTest test`
- Run one test method: `mvn -Dtest=SqlCheckerTest#shouldEnforceDatabasePrefixWhenEnabled test`
- Start the app: `mvn spring-boot:run`
- Clean and rerun: `mvn clean test`

## SQL file naming and type rules

Files must match: `<database>_<ddl|dml>_<submitter>.sql`

- **DDL files** (`_ddl_`): only `CREATE / ALTER / DROP / RENAME / TRUNCATE` — DML statements cause a `TYPE_MISMATCH ERROR`.
- **DML files** (`_dml_`): only `INSERT / REPLACE / UPDATE / DELETE` — DDL statements cause a `TYPE_MISMATCH ERROR`.
- Type purity is enforced in both `SqlChecker` (reports ERROR) and `SqlScriptAggregator` (aborts merge).

## Aggregation behavior

- Files matching `aggregation-skip-prefixes` are **copied as-is** to the aggregation output (not merged).
- Non-skipped files are grouped by `database + type + subdirectory`, merged by submitter order.
- `apollo/` directory is copied into `<aggregation>/apollo/`.
- All output files are normalized to **Unix line endings (LF) + UTF-8**, regardless of source encoding.

## Aggregation is conditional

Aggregation runs only when **no SQL file has an ERROR-severity issue** (WARNINGs are ignored). Scan errors and `TYPE_MISMATCH` both block aggregation.

## Key configuration (`application.yml` / `sqlcheck.*`)

| Property | Default | Notes |
|----------|---------|-------|
| `sql-dir` | `sql` | Input SQL directory; report and aggregation dirs auto-derived from its parent |
| `apollo-dir` | `apollo` | Apollo config directory |
| `aggregation-skip-prefixes` | `[]` | Files with these prefixes are copied verbatim, not merged |
| `database-check-enabled` | `false` | Requires `database-name` when enabled |
| `database-name` | `""` | Enforces `db.table` reference format |

## Useful references

- Default configuration: `src/main/resources/application.yml`
- Feature list and usage examples: `README.md`
