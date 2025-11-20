#!/bin/bash

# Start Spiron 7-Node Cluster for Large-Scale Benchmarking

echo "Starting Spiron 7-node cluster..."
echo "================================="
echo

# Kill existing processes
pkill -f "spiron-all.jar" 2>/dev/null || true
sleep 1

# Clean data directories
rm -rf /tmp/spiron/node-* 2>/dev/null || true

cd /Users/n0j04yp/Documents/personal/stormen-alpha/spiron

# Build peer list
PEERS="localhost:8081,localhost:8082,localhost:8083,localhost:8084,localhost:8085,localhost:8086,localhost:8087"

# Start 7 nodes
for i in {1..7}; do
  PORT=$((8080 + i))
  METRICS_PORT=$((9090 + i))
  NODE_ID="node-$i"
  DATA_DIR="/tmp/spiron/node-$i"
  
  echo "Starting Node $i (port $PORT, metrics $METRICS_PORT)..."
  java \
    -Dspiron.node.id=$NODE_ID \
    -Dspiron.port=$PORT \
    -Dspiron.cluster.peers=$PEERS \
    -Dspiron.storage.mode=solo \
    -Dspiron.metrics.port=$METRICS_PORT \
    -Dspiron.vector.dimensions=4096 \
    -Dspiron.profile=LOW_LATENCY \
    -Dspiron.data.dir=$DATA_DIR \
    -jar build/libs/spiron-all.jar > node$i.log 2>&1 &
    
  sleep 1
done

echo
echo "Waiting for nodes to initialize (5s)..."
sleep 5

echo
echo "Nodes started:"
for i in {1..7}; do
  PORT=$((8080 + i))
  METRICS_PORT=$((9090 + i))
  PID=$(pgrep -f "spiron.node.id=node-$i" | head -1)
  echo "  Node $i: PID $PID (http://localhost:$PORT, metrics http://localhost:$METRICS_PORT/metrics)"
done

echo
echo "Verifying nodes..."
ONLINE=0
for i in {1..7}; do
  METRICS_PORT=$((9090 + i))
  if curl -s -f http://localhost:$METRICS_PORT/metrics > /dev/null 2>&1; then
    echo "  ✓ Node $i (port $METRICS_PORT): ONLINE"
    ((ONLINE++))
  else
    echo "  ✗ Node $i (port $METRICS_PORT): NOT RESPONDING"
  fi
done

echo
echo "Cluster status: $ONLINE/7 nodes online"
echo "Logs: node1.log ... node7.log"
echo "To stop: pkill -f spiron-all.jar"
