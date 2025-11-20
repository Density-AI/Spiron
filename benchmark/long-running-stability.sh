#!/bin/bash
# Long-Running Stability Test for Spiron
# Runs a 24h soak test with continuous benchmarking

set -e

CLUSTER_SIZE=7
BASE_PORT=8081
METRICS_BASE=9091
DURATION_HOURS=24
BENCHMARK_REQUESTS=10000000
BENCHMARK_THREADS=20
BENCHMARK_DIM=4096

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Spiron Long-Running Stability Test${NC}"
echo "  Cluster Size: $CLUSTER_SIZE"
echo "  Duration: $DURATION_HOURS hours"
echo "  Benchmark: $BENCHMARK_REQUESTS requests, $BENCHMARK_THREADS threads, $BENCHMARK_DIM dimensions"
echo ""

# Cleanup
cleanup() {
    echo -e "${YELLOW}Cleaning up...${NC}"
    for i in $(seq 1 $CLUSTER_SIZE); do
        pkill -f "spiron.*808$i" 2>/dev/null || true
    done
}
trap cleanup EXIT INT TERM

# Build project
echo -e "${YELLOW}Building Spiron...${NC}"
./gradlew shadowJar -x test --quiet
echo -e "${GREEN}✓ Build complete${NC}"
echo ""

# Start cluster
echo -e "${YELLOW}Starting 7-node cluster...${NC}"
for i in $(seq 1 $CLUSTER_SIZE); do
    port=$((BASE_PORT + i - 1))
    metrics_port=$((METRICS_BASE + i - 1))
    peers=""
    for j in $(seq 1 $CLUSTER_SIZE); do
        if [ $j -ne $i ]; then
            peer_port=$((BASE_PORT + j - 1))
            if [ -z "$peers" ]; then
                peers="127.0.0.1:$peer_port"
            else
                peers="$peers,127.0.0.1:$peer_port"
            fi
        fi
    done
    java -jar build/libs/spiron-all.jar \
        --port=$port \
        --peers="$peers" \
        --metrics-port=$metrics_port \
        --profile=BALANCED \
        --storage=solo \
        > /tmp/spiron-node$i-stability.log 2>&1 &
    echo "  Node $i started on port $port (metrics: $metrics_port)"
done

echo -e "${YELLOW}Waiting for cluster to stabilize (30s)...${NC}"
sleep 30

echo -e "${YELLOW}Starting long-running benchmark...${NC}"
cd examples
java -cp build/libs/spiron-examples-*-all.jar com.spiron.BenchmarkClient \
    --target=http://localhost:8081 \
    --requests=$BENCHMARK_REQUESTS \
    --threads=$BENCHMARK_THREADS \
    --dimensions=$BENCHMARK_DIM \
    > /tmp/spiron-long-running-benchmark.txt 2>&1 &
BENCHMARK_PID=$!
cd ..

for h in $(seq 1 $DURATION_HOURS); do
    echo -e "${GREEN}Hour $h/$DURATION_HOURS: Cluster running...${NC}"
    sleep 3600
    # Optionally, collect metrics snapshot
    for i in $(seq 1 $CLUSTER_SIZE); do
        metrics_port=$((METRICS_BASE + i - 1))
        curl -s http://localhost:$metrics_port/metrics | grep -E "spiron_(crdt_commits_total|rpc_commit_total|rpc_failures_total)" | head -10 > /tmp/spiron-node${i}-metrics-hour${h}.txt
    done
    echo "  Metrics snapshot saved for hour $h."
done

echo -e "${GREEN}24h stability test complete!${NC}"
kill $BENCHMARK_PID 2>/dev/null || true
wait $BENCHMARK_PID 2>/dev/null || true

# Final metrics
for i in $(seq 1 $CLUSTER_SIZE); do
    metrics_port=$((METRICS_BASE + i - 1))
    echo "Node $i final metrics:"
    cat /tmp/spiron-node${i}-metrics-hour${DURATION_HOURS}.txt
    echo ""
done

echo -e "${GREEN}Logs: /tmp/spiron-node*-stability.log"
echo -e "Benchmark: /tmp/spiron-long-running-benchmark.txt"
echo -e "Metrics: /tmp/spiron-node*-metrics-hour*.txt"
