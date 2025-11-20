#!/bin/bash
# Quick Stability Test for Spiron (15 minute version)
# Validates methodology for long-running tests

set -e

CLUSTER_SIZE=7
BASE_PORT=8081
METRICS_BASE=9091
DURATION_MINUTES=15
BENCHMARK_REQUESTS=100000
BENCHMARK_THREADS=10
BENCHMARK_DIM=4096

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "=========================================="
echo "Spiron Quick Stability Test (15 min)"
echo "=========================================="
echo "  Cluster Size: $CLUSTER_SIZE nodes"
echo "  Duration: $DURATION_MINUTES minutes"
echo "  Workload: $BENCHMARK_REQUESTS requests, $BENCHMARK_THREADS threads, $BENCHMARK_DIM D"
echo ""

# Cleanup
cleanup() {
    echo ""
    echo -e "${YELLOW}Cleaning up...${NC}"
    for i in $(seq 1 $CLUSTER_SIZE); do
        pkill -f "spiron.*808$i" 2>/dev/null || true
    done
    echo -e "${GREEN}Cleanup complete${NC}"
}
trap cleanup EXIT INT TERM

# Build server fat jar
echo -e "${YELLOW}Building Spiron server...${NC}"
./gradlew :spiron-server:shadowJar -x test --quiet
echo -e "${GREEN}✓ Server build complete${NC}"
echo ""

# Start cluster
echo -e "${YELLOW}Starting 7-node cluster...${NC}"
for i in $(seq 1 $CLUSTER_SIZE); do
    port=$((BASE_PORT + i - 1))
    metrics_port=$((METRICS_BASE + i - 1))
    
    # Build peers list
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
    
    # Create unique storage directory
    mkdir -p /tmp/spiron-stability-test/node$i
    
    java -Dspiron.data.dir=/tmp/spiron-stability-test/node$i \
        -jar spiron-server/build/libs/spiron-server-all.jar \
        --port=$port \
        --peers="$peers" \
        --metrics-port=$metrics_port \
        --profile=BALANCED \
        --storage=solo \
        > /tmp/spiron-node$i-stability.log 2>&1 &
    
    echo "  Node $i started on port $port (metrics: $metrics_port)"
done

echo ""
echo -e "${YELLOW}Waiting for cluster to stabilize (15s)...${NC}"
sleep 15

# Verify cluster health
echo -e "${YELLOW}Verifying cluster health...${NC}"
healthy=0
for i in $(seq 1 $CLUSTER_SIZE); do
    metrics_port=$((METRICS_BASE + i - 1))
    if curl -s http://localhost:$metrics_port/metrics > /dev/null 2>&1; then
        echo -e "  ${GREEN}✓${NC} Node $i healthy"
        healthy=$((healthy + 1))
    else
        echo -e "  ${RED}✗${NC} Node $i unhealthy"
    fi
done

if [ $healthy -ne $CLUSTER_SIZE ]; then
    echo -e "${RED}ERROR: Only $healthy/$CLUSTER_SIZE nodes are healthy${NC}"
    exit 1
fi

echo -e "${GREEN}✓ All $CLUSTER_SIZE nodes healthy${NC}"
echo ""

# Collect baseline metrics
echo -e "${YELLOW}Collecting baseline metrics...${NC}"
for i in $(seq 1 $CLUSTER_SIZE); do
    metrics_port=$((METRICS_BASE + i - 1))
    curl -s http://localhost:$metrics_port/metrics | \
        grep -E "spiron_(crdt_commits_total|rpc_commit_total|rpc_failures_total|rpc_latency)" | \
        head -15 > /tmp/spiron-node${i}-metrics-t0.txt 2>&1
done
echo -e "${GREEN}✓ Baseline captured${NC}"
echo ""

# Start continuous benchmark using Java client BenchmarkClient
echo -e "${YELLOW}Starting continuous benchmark workload...${NC}"
./gradlew :spiron-clients:spiron-java-client:classes --quiet
java -cp "spiron-clients/spiron-java-client/build/classes/java/main:spiron-clients/spiron-java-client/build/generated/source/proto/main/java:spiron-clients/spiron-java-client/build/generated/source/proto/main/grpc:$(./gradlew -q :spiron-clients:spiron-java-client:dependencies --configuration runtimeClasspath | awk '/---/ {capture=1; next} /^$/ {capture=0} capture && /[\\-] / {print $2}' | tr '\n' ':' )" \
    com.spiron.client.examples.BenchmarkClient \
    $CLUSTER_SIZE $BENCHMARK_REQUESTS \
    > /tmp/spiron-stability-benchmark.txt 2>&1 &
BENCHMARK_PID=$!
echo -e "${GREEN}✓ Benchmark started (PID $BENCHMARK_PID)${NC}"
echo ""

# Monitor for duration
SNAPSHOT_INTERVAL=300  # 5 minutes
snapshots=$((DURATION_MINUTES / 5))

for s in $(seq 1 $snapshots); do
    elapsed=$((s * 5))
    echo -e "${YELLOW}[T+${elapsed}min] Taking metrics snapshot ${s}/${snapshots}...${NC}"
    
    # Collect metrics from all nodes
    for i in $(seq 1 $CLUSTER_SIZE); do
        metrics_port=$((METRICS_BASE + i - 1))
        
        # Check node health
        if curl -s http://localhost:$metrics_port/metrics > /dev/null 2>&1; then
            echo -e "  Node $i: ${GREEN}HEALTHY${NC}"
            curl -s http://localhost:$metrics_port/metrics | \
                grep -E "spiron_(crdt_commits_total|rpc_commit_total|rpc_failures_total|rpc_latency)" | \
                head -15 > /tmp/spiron-node${i}-metrics-t${elapsed}.txt 2>&1
        else
            echo -e "  Node $i: ${RED}UNHEALTHY${NC}"
        fi
    done
    
    # Wait for next snapshot (unless last one)
    if [ $s -lt $snapshots ]; then
        echo "  Waiting for next snapshot..."
        sleep $SNAPSHOT_INTERVAL
    fi
done

echo ""
echo -e "${GREEN}=========================================="
echo "Stability Test Complete!"
echo "==========================================${NC}"
echo ""

# Stop benchmark if still running
kill $BENCHMARK_PID 2>/dev/null || true
wait $BENCHMARK_PID 2>/dev/null || true

# Final health check
echo -e "${YELLOW}Final cluster health check:${NC}"
final_healthy=0
for i in $(seq 1 $CLUSTER_SIZE); do
    metrics_port=$((METRICS_BASE + i - 1))
    if curl -s http://localhost:$metrics_port/metrics > /dev/null 2>&1; then
        echo -e "  Node $i: ${GREEN}HEALTHY${NC}"
        final_healthy=$((final_healthy + 1))
    else
        echo -e "  Node $i: ${RED}UNHEALTHY${NC}"
    fi
done

echo ""
echo "Results Summary:"
echo "  Duration: ${DURATION_MINUTES} minutes"
echo "  Final Health: $final_healthy/$CLUSTER_SIZE nodes"
echo "  Snapshots Collected: $snapshots"
echo ""
echo "Validation Checklist:"
if [ $final_healthy -eq $CLUSTER_SIZE ]; then
    echo -e "  ${GREEN}✓${NC} No node crashes"
else
    echo -e "  ${RED}✗${NC} Some nodes failed"
fi
echo -e "  ${YELLOW}?${NC} Memory leaks - Check JVM metrics"
echo -e "  ${YELLOW}?${NC} Throughput consistency - Check RPC commit rates"
echo -e "  ${YELLOW}?${NC} Latency stability - Check P99 latency trends"
echo ""
echo "Output Files:"
echo "  Logs: /tmp/spiron-node*-stability.log"
echo "  Benchmark: /tmp/spiron-stability-benchmark.txt"
echo "  Metrics: /tmp/spiron-node*-metrics-t*.txt"
echo ""

# Show sample metrics comparison (node 1)
echo -e "${YELLOW}Node 1 Metrics Comparison:${NC}"
echo "Baseline (T+0):"
head -5 /tmp/spiron-node1-metrics-t0.txt 2>/dev/null || echo "  (no data)"
echo ""
echo "Final (T+${DURATION_MINUTES}min):"
head -5 /tmp/spiron-node1-metrics-t${DURATION_MINUTES}.txt 2>/dev/null || echo "  (no data)"
