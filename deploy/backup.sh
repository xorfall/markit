#!/usr/bin/env bash
#
# backup.sh — Postgres logical backup for markit (mitigates R-OPS-01: total data loss).
#
# Postgres is the sole source of truth (ADR-0002). Elasticsearch is a rebuildable
# projection, so backups target Postgres only. Dumps use the custom format (-Fc), which
# is compressed and restorable selectively with pg_restore (see restore.sh).
#
# Runs both on the host (against the mapped 5432) and inside a container (host=postgres).
# Configured with the SAME env vars as the compose stack; localhost defaults let it run
# unconfigured against a dev stack.
#
# Env:
#   DB_HOST         Postgres host            (default: localhost)
#   DB_PORT         Postgres port            (default: 5432)
#   DB_NAME         Database name            (default: markit)
#   DB_USER         Database user            (default: markit)
#   PGPASSWORD      Password (falls back to DB_PASSWORD, then 'markit')
#   BACKUP_DIR      Output directory         (default: ./backups)
#   RETENTION_DAYS  Prune dumps older than N days (default: 14)
#
# Exit codes: 0 ok · 1 usage · 2 dump failed · 3 pg_dump missing
set -euo pipefail

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

case "${1:-}" in
  -h|--help) usage 0 ;;
  "") : ;;
  *) echo "error: unexpected argument '$1'" >&2; usage 1 ;;
esac

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-markit}"
DB_USER="${DB_USER:-markit}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-markit}}"

log() { printf '%s [backup] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"; }

if ! command -v pg_dump >/dev/null 2>&1; then
  log "ERROR: pg_dump not found on PATH (install postgresql-client or run inside a postgres:16 container)"
  exit 3
fi

mkdir -p "$BACKUP_DIR"

STAMP="$(date -u +'%Y%m%dT%H%M%SZ')"
OUT="${BACKUP_DIR%/}/markit-${DB_NAME}-${STAMP}.dump"
TMP="${OUT}.partial"

log "dumping db='${DB_NAME}' host='${DB_HOST}:${DB_PORT}' user='${DB_USER}' -> ${OUT}"

# Dump to a .partial file first, then atomically rename, so an interrupted run never
# leaves a truncated dump that looks valid to the pruner or restore.
if ! pg_dump \
      --host="$DB_HOST" \
      --port="$DB_PORT" \
      --username="$DB_USER" \
      --dbname="$DB_NAME" \
      --format=custom \
      --no-owner \
      --no-privileges \
      --file="$TMP"; then
  log "ERROR: pg_dump failed; removing partial file"
  rm -f "$TMP"
  exit 2
fi

mv "$TMP" "$OUT"
SIZE="$(du -h "$OUT" | cut -f1)"
log "wrote ${OUT} (${SIZE})"

# Prune dumps older than the retention window. Only our own naming pattern is touched.
log "pruning dumps older than ${RETENTION_DAYS} day(s) in ${BACKUP_DIR}"
PRUNED=0
while IFS= read -r -d '' old; do
  rm -f "$old"
  log "pruned $(basename "$old")"
  PRUNED=$((PRUNED + 1))
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name "markit-*.dump" -mtime "+${RETENTION_DAYS}" -print0 2>/dev/null)

REMAINING="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'markit-*.dump' | wc -l | tr -d ' ')"
log "done: pruned=${PRUNED} remaining=${REMAINING}"
