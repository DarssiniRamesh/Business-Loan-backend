#!/usr/bin/env bash
set -euo pipefail

# Simple one-command runner for manual backend preview.
# - Uses the project's Gradle wrapper (no global Gradle required).
# - Does NOT modify any platform preview behavior; it just starts the same server locally.
# - Swagger UI + OpenAPI endpoints are printed after start.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${SCRIPT_DIR}/BusinessLoanAPI(SpringBoot)"
# Resolve .env relative to where the user runs the script from (requested behavior).
# This avoids surprises when `./script.sh` is invoked from different directories.
RUN_DIR="$(pwd -P)"

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
echo "Run directory: ${RUN_DIR}"
echo

# Load environment variables from .env if present (local/dev convenience).
# We intentionally do not fail if missing because CI/orchestrated environments may inject env vars differently.
#
# IMPORTANT: Prefer .env relative to *current directory* first (requested),
# then fall back to script dir and app dir.
ENV_FILE_CANDIDATES=(
  "${RUN_DIR}/.env"
  "${RUN_DIR}/BusinessLoanAPI(SpringBoot)/.env"
  "${SCRIPT_DIR}/.env"
  "${APP_DIR}/.env"
)

ENV_FILE_LOADED=""
for ENV_FILE in "${ENV_FILE_CANDIDATES[@]}"; do
  if [[ -f "${ENV_FILE}" ]]; then
    ENV_FILE_LOADED="${ENV_FILE}"
    # shellcheck disable=SC1090
    set -a
    source "${ENV_FILE}"
    set +a
    break
  fi
done

if [[ -n "${ENV_FILE_LOADED}" ]]; then
  echo "Loaded .env: ${ENV_FILE_LOADED}"
else
  echo "Loaded .env: (none found)"
fi

# Some environments expose Neon as NEON_DATABASE_URL; normalize to DATABASE_URL if needed.
URL_SOURCE=""
if [[ -n "${DATABASE_URL:-}" ]]; then
  URL_SOURCE="DATABASE_URL"
elif [[ -n "${NEON_DATABASE_URL:-}" ]]; then
  export DATABASE_URL="${NEON_DATABASE_URL}"
  URL_SOURCE="NEON_DATABASE_URL"
fi

# Spring Boot datasource expects a JDBC URL. Neon commonly provides URI form (postgresql://...).
# Convert URI -> JDBC for local runs, preserving any query string.
# Examples:
#   postgresql://user:pass@host/db?sslmode=require&channel_binding=require
#   -> jdbc:postgresql://host/db?sslmode=require&channel_binding=require&user=user&password=pass
if [[ -n "${DATABASE_URL:-}" && "${DATABASE_URL}" != jdbc:* ]]; then
  if [[ "${DATABASE_URL}" == postgresql://* || "${DATABASE_URL}" == postgres://* ]]; then
    JDBC_URL="$(python3 - <<'PY'
import os, sys
from urllib.parse import urlparse, parse_qsl, urlencode

raw = os.environ.get("DATABASE_URL", "").strip()
p = urlparse(raw)

if p.scheme not in ("postgresql", "postgres"):
    print(raw)
    sys.exit(0)

host = p.hostname or ""
port = f":{p.port}" if p.port else ""
db = (p.path or "").lstrip("/")
base = f"jdbc:postgresql://{host}{port}/{db}"

params = dict(parse_qsl(p.query, keep_blank_values=True))
if p.username:
    params.setdefault("user", p.username)
if p.password:
    params.setdefault("password", p.password)

qs = urlencode(params) if params else ""
jdbc = base + (f"?{qs}" if qs else "")
print(jdbc)
PY
    )"
    export DATABASE_URL="${JDBC_URL}"
  fi
fi

# Also export Spring's conventional env var (highest compatibility for bootstrapping).
if [[ -n "${DATABASE_URL:-}" ]]; then
  export SPRING_DATASOURCE_URL="${DATABASE_URL}"
fi

# Print resolved JDBC url (mask password) to make failures obvious without leaking secrets.
# This is explicit debug output requested by the task.
if [[ -n "${DATABASE_URL:-}" ]]; then
  MASKED_DATABASE_URL="${DATABASE_URL}"
  if [[ "${DATABASE_URL}" == *"password="* ]]; then
    MASKED_DATABASE_URL="$(python3 - <<'PY'
import os
url = os.environ.get("DATABASE_URL","")
if "password=" not in url:
    print(url)
    raise SystemExit(0)
prefix, rest = url.split("password=", 1)
if "&" in rest:
    _, suffix = rest.split("&", 1)
    print(prefix + "password=***&" + suffix)
else:
    print(prefix + "password=***")
PY
    )"
  fi
  if [[ -n "${URL_SOURCE}" ]]; then
    echo "Resolved database URL source: ${URL_SOURCE}"
  fi
  echo "Resolved DATABASE_URL (masked): ${MASKED_DATABASE_URL}"
else
  echo "Resolved DATABASE_URL: (empty)"
fi

# Helpful reminder (do not block startup if not set; Spring may still start depending on your config)
if [[ -z "${DATABASE_URL:-}" || -z "${JWT_SECRET:-}" ]]; then
  cat <<'EOF'
NOTE: Some environment variables are not set in this shell.
The app uses (see application.properties):
  DATABASE_URL (JDBC), DATABASE_USERNAME, DATABASE_PASSWORD, JWT_SECRET

This script will source .env if present and will convert postgresql://... into jdbc:postgresql://...
If you rely on the platform/orchestrated environment, ensure these are injected there.

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

# Pass datasource settings deterministically.
# We both export env vars AND pass explicit system properties. This prevents failures where Gradle's
# bootRun child process doesn't see env vars due to sourcing issues.
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-${DATABASE_URL:-}}"

exec env \
  DATABASE_URL="${DATABASE_URL:-}" \
  SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-}" \
  DATABASE_USERNAME="${DATABASE_USERNAME:-}" \
  DATABASE_PASSWORD="${DATABASE_PASSWORD:-}" \
  JWT_SECRET="${JWT_SECRET:-}" \
  ./gradlew bootRun \
    -Dspring.datasource.url="${SPRING_DATASOURCE_URL:-}" \
    -Dspring.datasource.username="${DATABASE_USERNAME:-}" \
    -Dspring.datasource.password="${DATABASE_PASSWORD:-}"
