# Flyway migrations (BusinessLoanAPISpringBoot)

This project uses Flyway with the default location:

- `classpath:db/migration`

## Versioning convention

We use Flyway's standard SQL migration naming:

- `V<version>__<description>.sql`

Example:

- `V1__baseline_auth_tables.sql`

### IMPORTANT

- Do **not** use zero-padded versions like `V001__...` in this codebase.
- The current baseline + schema for auth tables is provided by `V1__baseline_auth_tables.sql`.
- Hibernate is configured with `spring.jpa.hibernate.ddl-auto=validate`, so migrations must match entity mappings exactly.
"""
