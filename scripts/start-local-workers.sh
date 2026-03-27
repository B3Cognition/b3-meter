#!/bin/bash
#
# Start N local worker nodes on different ports.
# Usage: ./scripts/start-local-workers.sh [count]
#   Default: 3 workers
#
# Workers will run on:
#   Worker 1: gRPC=9091, HTTP=8091
#   Worker 2: gRPC=9092, HTTP=8092
#   Worker 3: gRPC=9093, HTTP=8093
#   ...etc
#
# Stop all: ./scripts/stop-local-workers.sh
#   or: kill $(cat /tmp/jmeter-worker-*.pid)
#

set -e
cd "$(dirname "$0")/.."

WORKER_COUNT=${1:-3}
WORKER_JAR="modules/worker-node/build/libs/worker-node.jar"

if [ ! -f "$WORKER_JAR" ]; then
  echo "Worker jar not found. Building..."
  ./gradlew :modules:worker-node:bootJar -q
fi

echo "============================================"
echo "  Starting $WORKER_COUNT local workers"
echo "============================================"

WORKER_ADDRESSES=""

for i in $(seq 1 $WORKER_COUNT); do
  GRPC_PORT=$((9090 + i))
  HTTP_PORT=$((8090 + i))
  WORKER_NAME="local-worker-$i"
  PID_FILE="/tmp/jmeter-worker-$i.pid"
  LOG_FILE="/tmp/jmeter-worker-$i.log"

  # Kill existing worker on this port
  if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    kill "$OLD_PID" 2>/dev/null || true
    sleep 1
  fi
  lsof -ti :"$GRPC_PORT" | xargs kill -9 2>/dev/null || true
  lsof -ti :"$HTTP_PORT" | xargs kill -9 2>/dev/null || true

  echo "  Starting $WORKER_NAME (gRPC=$GRPC_PORT, HTTP=$HTTP_PORT)..."

  java -jar "$WORKER_JAR" \
    --server.port="$HTTP_PORT" \
    --grpc.port="$GRPC_PORT" \
    --worker.name="$WORKER_NAME" \
    > "$LOG_FILE" 2>&1 &

  echo $! > "$PID_FILE"

  if [ -n "$WORKER_ADDRESSES" ]; then
    WORKER_ADDRESSES="$WORKER_ADDRESSES,"
  fi
  WORKER_ADDRESSES="${WORKER_ADDRESSES}localhost:${GRPC_PORT}"
done

echo ""
echo "Waiting for workers to start..."
sleep 5

echo ""
echo "============================================"
echo "  Worker Status"
echo "============================================"

ALL_UP=true
for i in $(seq 1 $WORKER_COUNT); do
  HTTP_PORT=$((8090 + i))
  GRPC_PORT=$((9090 + i))
  WORKER_NAME="local-worker-$i"

  if curl -sf "http://localhost:$HTTP_PORT/health" >/dev/null 2>&1 || \
     curl -sf "http://localhost:$HTTP_PORT/actuator/health" >/dev/null 2>&1; then
    echo "  🟢 $WORKER_NAME — UP (gRPC=$GRPC_PORT, HTTP=$HTTP_PORT)"
  else
    echo "  🔴 $WORKER_NAME — DOWN (check /tmp/jmeter-worker-$i.log)"
    ALL_UP=false
  fi
done

echo ""
echo "Worker addresses for controller:"
echo "  $WORKER_ADDRESSES"
echo ""
echo "Register in UI → Distributed Config, or pass in API:"
echo "  curl -X POST http://localhost:8080/api/v1/runs \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"planId\":\"...\",\"workerAddresses\":[\"${WORKER_ADDRESSES//,/\",\"}\"]}'  "
echo ""
echo "Logs: /tmp/jmeter-worker-{1..$WORKER_COUNT}.log"
echo "Stop:  ./scripts/stop-local-workers.sh"
echo "============================================"
