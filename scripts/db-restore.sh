#!/bin/bash
# -----------------------------------------------------------------------
# db-restore.sh — Restores a jMeter Next database from a backup file.
#
# Supports two modes:
#   H2 (default)   — copies the .mv.db backup into place
#   PostgreSQL      — runs psql to load the SQL dump (set DB_TYPE=postgres)
#
# Usage:
#   ./scripts/db-restore.sh backups/jmeter-next-20260326-120000.mv.db
#   DB_TYPE=postgres ./scripts/db-restore.sh backups/jmeter-next-20260326-120000.sql
# -----------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP="$1"
DB_TYPE="${DB_TYPE:-h2}"

if [ -z "${BACKUP:-}" ]; then
  echo "Usage: ./scripts/db-restore.sh <backup-file>"
  echo "  Set DB_TYPE=postgres for PostgreSQL restores"
  exit 1
fi

if [ ! -f "$BACKUP" ]; then
  echo "ERROR: Backup file not found: $BACKUP"
  exit 1
fi

case "$DB_TYPE" in
  h2)
    DEST="${PROJECT_ROOT}/data/jmeter-next.mv.db"
    mkdir -p "$(dirname "$DEST")"
    cp "$BACKUP" "$DEST"
    echo "H2 database restored from $BACKUP"
    ;;
  postgres)
    PG_HOST="${PG_HOST:-localhost}"
    PG_PORT="${PG_PORT:-5432}"
    PG_USER="${PG_USER:-jmeter}"
    PG_DB="${PG_DB:-jmeter_next}"
    psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" "$PG_DB" < "$BACKUP"
    echo "PostgreSQL database restored from $BACKUP"
    ;;
  *)
    echo "ERROR: Unknown DB_TYPE=$DB_TYPE (expected h2 or postgres)"
    exit 1
    ;;
esac
