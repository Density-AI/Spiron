#!/bin/bash
echo "Starting Spiron 7-node cluster (4096 dimensions)..."
echo "================================="
echo ""

for i in {1..7}; do
  PORT=$((8080 + i))
  METRICS_PORT=$((9090 + i))
  echo "Starting Node $i (port $PORT, metrics $METRICS_PORT)..."
  java -Dspiron.node.id=node-$i \
       -Dspiron.port=$PORT \
       -Dspiron.cluster.peers=localhost:8081,localhost:8082,localhost:8083,localhost:8084,localhost:8085,localhost:8086,localhost:8087 \
       -Dspiron.storage.mode=solo \
       -Dspiron.metrics.port=$METRICS_PORT \
       -Dspiron.vector.dimensions=4096 \
       -Dspiron.profile=LOW_LATENCY \
       -Dspiron.data.dir=/tmp/spiron/node-$i \
       -Xmx2g -Xms1g \
       -jar ../build/libs/spiron-all.jar > node$i.log 2>&1 &
  echo $! > node$i.pid
done

echo ""
echo "Waiting for nodes to initialize (10s)..."
sleep 10

echo ""
echo "Nodes started:"
for i in {1..7}; do
  PID=$(cat node$i.pid)
  PORT=$((8080 + i))
  METRICS_PORT=$((9090 + i))
  echo "  Node $i: PID $PID (http://localhost:$PORT, metrics http://localhost:$METRICS_PORT/metrics)"
done

echo ""
echo "Verifying nodes..."
for i in {1..7}; do
  METRICS_PORT=$((9090 + i))
  if curl -s http://localhost:$METRICS_PORT/metrics > /dev/null 2>&1; then
    echo "  ✓ Node $i (port $METRICS_PORT): ONLINE"
  else
    echo "  ✗ Node $i (port $METRICS_PORT): OFFLINE"
  fi
done

echo ""
ONLINE=$(ps aux | grep "spiron-all.jar" | grep -v grep | wc -l | tr -d ' ')
echo "Cluster status: $ONLINE/7 nodes online"
echo "Profile: LOW_LATENCY (α=0.75, siphon=0.3, angular=0.5, commit=0.7)"
echo "Logs: node1.log ... node7.log"
echo "To stop: pkill -f spiron-all.jar"
