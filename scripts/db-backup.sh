#!/bin/bash
# -----------------------------------------------------------------------
# db-backup.sh — Creates a timestamped backup of the jMeter Next database.
#
# Supports two modes:
#   H2 (default)   — copies the .mv.db file
#   PostgreSQL      — runs pg_dump (set DB_TYPE=postgres)
#
# Usage:
#   ./scripts/db-backup.sh              # H2 backup
#   DB_TYPE=postgres ./scripts/db-backup.sh   # PostgreSQL backup
# -----------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_DIR="${PROJECT_ROOT}/backups"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
DB_TYPE="${DB_TYPE:-h2}"

mkdir -p "$BACKUP_DIR"

case "$DB_TYPE" in
  h2)
    SRC="${PROJECT_ROOT}/data/jmeter-next.mv.db"
    if [ ! -f "$SRC" ]; then
      echo "ERROR: H2 database not found at $SRC"
      exit 1
    fi
    DEST="${BACKUP_DIR}/jmeter-next-${TIMESTAMP}.mv.db"
    cp "$SRC" "$DEST"
    echo "H2 backup complete: $DEST ($(du -h "$DEST" | cut -f1))"
    ;;
  postgres)
    PG_HOST="${PG_HOST:-localhost}"
    PG_PORT="${PG_PORT:-5432}"
    PG_USER="${PG_USER:-jmeter}"
    PG_DB="${PG_DB:-jmeter_next}"
    DEST="${BACKUP_DIR}/jmeter-next-${TIMESTAMP}.sql"
    pg_dump -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" "$PG_DB" > "$DEST"
    echo "PostgreSQL backup complete: $DEST ($(du -h "$DEST" | cut -f1))"
    ;;
  *)
    echo "ERROR: Unknown DB_TYPE=$DB_TYPE (expected h2 or postgres)"
    exit 1
    ;;
esac
