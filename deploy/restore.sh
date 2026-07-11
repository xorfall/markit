#!/usr/bin/env bash
#
# restore.sh — restore a markit Postgres dump produced by backup.sh (custom -Fc format).
#
# DESTRUCTIVE: --clean --if-exists drops and recreates every object in the target database
# before loading. It therefore refuses to run unless CONFIRM=yes is set in the environment.
#
# Usage:
#   CONFIRM=yes ./restore.sh <path-to-dump>
#   ./restore.sh --help
#
# Env (same as backup.sh / the compose stack):
#   DB_HOST     Postgres host   (default: localhost)
#   DB_PORT     Postgres port   (default: 5432)
#   DB_NAME     Database name   (default: markit)
#   DB_USER     Database user   (default: markit)
#   PGPASSWORD  Password (falls back to DB_PASSWORD, then 'markit')
#   CONFIRM     Must equal 'yes' to proceed (safety guard)
#
# After a successful restore, Elasticsearch still holds the OLD projection. Postgres is the
# source of truth, so rebuild the search index (see printed next steps below).
#
# Exit codes: 0 ok · 1 usage · 2 missing/unreadable dump · 3 confirmation missing ·
#             4 pg_restore missing · 5 restore failed
set -euo pipefail

usage() {
  sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

DUMP=""
case "${1:-}" in
  -h|--help) usage 0 ;;
  "") echo "error: missing <dump> argument" >&2; usage 1 ;;
  -*) echo "error: unknown option '$1'" >&2; usage 1 ;;
  *) DUMP="$1" ;;
esac

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-markit}"
DB_USER="${DB_USER:-markit}"
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-markit}}"

log() { printf '%s [restore] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"; }

if [ ! -f "$DUMP" ] || [ ! -r "$DUMP" ]; then
  log "ERROR: dump file not found or unreadable: ${DUMP}"
  exit 2
fi

if [ "${CONFIRM:-}" != "yes" ]; then
  log "REFUSING to run: this OVERWRITES database '${DB_NAME}' on ${DB_HOST}:${DB_PORT}."
  log "Re-run with CONFIRM=yes to proceed, e.g.:"
  log "  CONFIRM=yes DB_HOST=${DB_HOST} ./restore.sh ${DUMP}"
  exit 3
fi

if ! command -v pg_restore >/dev/null 2>&1; then
  log "ERROR: pg_restore not found on PATH (install postgresql-client or run inside a postgres:16 container)"
  exit 4
fi

log "restoring '${DUMP}' into db='${DB_NAME}' host='${DB_HOST}:${DB_PORT}' user='${DB_USER}' (--clean --if-exists)"

# --clean --if-exists drops existing objects first; --no-owner/--no-privileges keep the
# restore portable across users. --exit-on-error surfaces a genuinely broken restore.
if ! pg_restore \
      --host="$DB_HOST" \
      --port="$DB_PORT" \
      --username="$DB_USER" \
      --dbname="$DB_NAME" \
      --clean \
      --if-exists \
      --no-owner \
      --no-privileges \
      --exit-on-error \
      "$DUMP"; then
  log "ERROR: pg_restore failed"
  exit 5
fi

log "restore complete."
cat <<'NEXT'

────────────────────────────────────────────────────────────────────────
NEXT STEPS — rebuild the search projection
────────────────────────────────────────────────────────────────────────
Postgres is now restored, but Elasticsearch still holds the pre-restore
projection. ES is a *rebuildable* read model (ADR-0002, architecture §4.4),
so rebuild it from Postgres with the backend admin reindex endpoint:

  # obtain an access token (see api-contract.md), then:
  curl -fsS -X POST http://localhost:8080/api/v1/admin/reindex \
       -H "Authorization: Bearer <ACCESS_TOKEN>"
  # -> {"indexed": <n>}   (rebuilds ES from Postgres, NFR-CONS-004)

Then verify:
  * backend readiness:  curl -fsS http://localhost:8080/actuator/health/readiness
  * a search returns expected results (normal, non-degraded mode)
  * row counts / a known bookmark are present in Postgres
────────────────────────────────────────────────────────────────────────
NEXT
