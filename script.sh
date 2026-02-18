#!/usr/bin/env bash
set -euo pipefail

# Simple one-command runner for manual backend preview.
# - Uses the project's Gradle wrapper (no global Gradle required).
# - Does NOT modify any platform preview behavior; it just starts the same server locally.
# - Swagger UI + OpenAPI endpoints are printed after start.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${SCRIPT_DIR}/BusinessLoanAPI(SpringBoot)"

if [[ ! -d "${APP_DIR}" ]]; then
  echo "ERROR: Spring Boot project directory not found: ${APP_DIR}" >&2
  exit 1
fi

if [[ ! -x "${APP_DIR}/gradlew" ]]; then
  echo "ERROR: gradlew not found or not executable at: ${APP_DIR}/gradlew" >&2
  echo "Try: chmod +x \"${APP_DIR}/gradlew\"" >&2
  exit 1
fi

echo "Starting Business Loan Spring Boot API..."
echo "Project: ${APP_DIR}"
echo

# Helpful reminder (do not block startup if not set; Spring may still start depending on your config)
if [[ -z "${DATABASE_URL:-}" || -z "${DATABASE_USERNAME:-}" || -z "${DATABASE_PASSWORD:-}" || -z "${JWT_SECRET:-}" ]]; then
  cat <<'EOF'
NOTE: Some environment variables are not set in this shell.
The app uses (see application.properties):
  DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, JWT_SECRET

If you rely on the container's .env file, run from the platform/orchestrated environment,
or export them in your terminal before running this script.

EOF
fi

cat <<'EOF'
Swagger/OpenAPI (based on current application.properties):
  Swagger UI: http://localhost:3010/swagger-ui/index.html
  OpenAPI JSON: http://localhost:3010/v3/api-docs

Health (actuator):
  http://localhost:3010/actuator/health

Press Ctrl+C to stop the server.
EOF

cd "${APP_DIR}"
exec ./gradlew bootRun
