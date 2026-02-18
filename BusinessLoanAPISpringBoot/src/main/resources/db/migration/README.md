# Flyway migrations (BusinessLoanAPISpringBoot)

This project uses Flyway with the default location:

- `classpath:db/migration`

## Current state (reconciled)

- This codebase currently has **one** SQL migration:
  - `V1__baseline_auth_tables.sql`

There are **no** `V001__...` / `V01__...` / duplicate-baseline migrations in this repository, so Flyway sees a single consistent version sequence.

## Versioning convention

We use Flyway's standard SQL migration naming:

- `V<version>__<description>.sql`

Example:

- `V1__baseline_auth_tables.sql`

### IMPORTANT

- Do **not** use zero-padded versions like `V001__...` in this codebase.
- Do **not** introduce multiple “baseline” migrations; extend the schema using `V2__...`, `V3__...`, etc.
- Hibernate is configured with `spring.jpa.hibernate.ddl-auto=validate`, so migrations must match entity mappings exactly.

## Schema coverage

`V1__baseline_auth_tables.sql` defines the tables required by the current JPA entities:
- `users`
- `email_verification_tokens`
- `audit_log`
