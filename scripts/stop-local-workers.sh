#!/bin/bash
#
# Stop all local worker nodes.
#

echo "Stopping local workers..."

STOPPED=0
for PID_FILE in /tmp/jmeter-worker-*.pid; do
  [ -f "$PID_FILE" ] || continue
  PID=$(cat "$PID_FILE")
  WORKER=$(basename "$PID_FILE" .pid | sed 's/jmeter-//')
  if kill "$PID" 2>/dev/null; then
    echo "  Stopped $WORKER (PID $PID)"
    STOPPED=$((STOPPED + 1))
  fi
  rm -f "$PID_FILE"
done

# Also kill by port range 9091-9099 and 8091-8099
for PORT in $(seq 9091 9099) $(seq 8091 8099); do
  lsof -ti :"$PORT" 2>/dev/null | xargs kill -9 2>/dev/null || true
done

echo ""
echo "Stopped $STOPPED worker(s)."
