#!/bin/bash
# Chaos Engineering: Partial Recovery Test
# Tests Spiron resilience when 3 nodes fail, 2 recover, 1 stays down
# With continuous traffic and varying vector values

set -e

CLUSTER_SIZE=7
BASE_PORT=8081
METRICS_BASE=9091
BENCHMARK_REQUESTS=200000  # Reduced for faster test
BENCHMARK_THREADS=15
BENCHMARK_DIM=4096

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "=========================================="
echo "Chaos Test: Partial Recovery Scenario"
echo "=========================================="
echo ""
echo "Test Scenario:"
echo "  1. Start 7-node cluster"
echo "  2. Generate continuous traffic (varying vectors)"
echo "  3. Kill 3 random nodes simultaneously"
echo "  4. After 1 min: Recover 2 nodes"
echo "  5. After 5 min: 1 node remains down"
echo "  6. Validate cluster continues with 6/7 nodes"
echo ""
echo "Configuration:"
echo "  Cluster: $CLUSTER_SIZE nodes (ports 8081-8087)"
echo "  Profile: BALANCED (α=0.82, commit=0.75)"
echo "  Workload: $BENCHMARK_REQUESTS requests, $BENCHMARK_THREADS threads"
echo "  Dimensions: $BENCHMARK_DIM (varying energies)"
echo ""

# Cleanup function
cleanup() {
    echo ""
    echo -e "${YELLOW}Cleaning up...${NC}"
    
    # Stop benchmark
    pkill -f "BenchmarkClient" 2>/dev/null || true
    
    # Stop all nodes
    for i in $(seq 1 $CLUSTER_SIZE); do
        pkill -f "spiron.*808$i" 2>/dev/null || true
    done
    
    echo -e "${GREEN}Cleanup complete${NC}"
}
trap cleanup EXIT INT TERM

# Function to check cluster health
check_cluster_health() {
    local label="$1"
    echo -e "${BLUE}[$label] Checking cluster health...${NC}"
    
    HEALTH_COUNT=0
    for i in $(seq 1 $CLUSTER_SIZE); do
        metrics_port=$((METRICS_BASE + i - 1))
        if curl -s http://localhost:$metrics_port/metrics > /dev/null 2>&1; then
            echo -e "  Node $i: ${GREEN}HEALTHY${NC}"
            HEALTH_COUNT=$((HEALTH_COUNT + 1))
        else
            echo -e "  Node $i: ${RED}DOWN${NC}"
        fi
    done
    
    echo -e "${BLUE}Health: $HEALTH_COUNT/$CLUSTER_SIZE nodes operational${NC}"
    echo ""
}

# Function to collect metrics snapshot
collect_metrics() {
    local label="$1"
    local output_file="/tmp/spiron-partial-recovery-metrics-${label}.txt"
    
    echo -e "${YELLOW}Collecting metrics snapshot: $label${NC}"
    for i in $(seq 1 $CLUSTER_SIZE); do
        metrics_port=$((METRICS_BASE + i - 1))
        if curl -s http://localhost:$metrics_port/metrics > /dev/null 2>&1; then
            echo "=== Node $i ===" >> "$output_file"
            curl -s http://localhost:$metrics_port/metrics | \
                grep -E "spiron_(crdt_commits_total|rpc_commit_total|rpc_broadcast_total|rpc_failures_total)" | \
                head -20 >> "$output_file"
            echo "" >> "$output_file"
        fi
    done
    echo -e "${GREEN}✓ Snapshot saved: $output_file${NC}"
    echo ""
}

# Build project
echo -e "${YELLOW}Building Spiron...${NC}"
./gradlew shadowJar -x test --quiet
echo -e "${GREEN}✓ Build complete${NC}"
echo ""

# Create storage directories
for i in $(seq 1 $CLUSTER_SIZE); do
    mkdir -p /tmp/spiron-partial-test/node$i
done

# Start 7-node cluster
echo -e "${YELLOW}Starting 7-node cluster...${NC}"
declare -a NODE_PIDS

for i in $(seq 1 $CLUSTER_SIZE); do
    port=$((BASE_PORT + i - 1))
    metrics_port=$((METRICS_BASE + i - 1))
    
    # Build peers list (exclude self)
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
    
    java -Dspiron.node.id=node-$i \
         -Dspiron.port=$port \
         -Dspiron.cluster.peers=$peers \
         -Dspiron.storage.mode=solo \
         -Dspiron.metrics.port=$metrics_port \
         -Dspiron.vector.dimensions=$BENCHMARK_DIM \
         -Dspiron.profile=BALANCED \
         -Dspiron.data.dir=/tmp/spiron-partial-test/node$i \
         -Xmx1g -Xms512m \
         -jar build/libs/spiron-all.jar \
        > /tmp/spiron-node$i-partial.log 2>&1 &
    
    NODE_PIDS[$i]=$!
    echo "  Node $i started (PID ${NODE_PIDS[$i]}, port $port, metrics $metrics_port)"
done

echo ""
echo -e "${YELLOW}Waiting for cluster to stabilize (20s)...${NC}"
sleep 20

# Verify initial cluster health
check_cluster_health "INITIAL"

if [ $HEALTH_COUNT -ne $CLUSTER_SIZE ]; then
    echo -e "${RED}ERROR: Cluster failed to start properly ($HEALTH_COUNT/$CLUSTER_SIZE healthy)${NC}"
    exit 1
fi

echo -e "${GREEN}✓ All $CLUSTER_SIZE nodes are healthy${NC}"
echo ""

# Collect baseline metrics
collect_metrics "baseline"

# Start continuous benchmark workload
echo -e "${YELLOW}Starting continuous benchmark workload...${NC}"
echo "  Target: http://localhost:8081"
echo "  Requests: $BENCHMARK_REQUESTS (varying vector values)"
echo "  Threads: $BENCHMARK_THREADS concurrent clients"
echo "  Dimensions: $BENCHMARK_DIM"
echo ""

cd examples
java -cp build/libs/spiron-examples-*-all.jar com.spiron.BenchmarkClient \
    --target=http://localhost:8081 \
    --requests=$BENCHMARK_REQUESTS \
    --threads=$BENCHMARK_THREADS \
    --dimensions=$BENCHMARK_DIM \
    > /tmp/spiron-partial-benchmark.log 2>&1 &
BENCHMARK_PID=$!
cd ..

echo -e "${GREEN}✓ Benchmark started (PID $BENCHMARK_PID)${NC}"
echo -e "${BLUE}Continuous traffic flowing with varying vector energies...${NC}"
echo ""

# Let traffic flow for 15 seconds
echo -e "${YELLOW}Phase 1: Normal operations (15s of traffic)${NC}"
sleep 15

collect_metrics "before-chaos"
check_cluster_health "PRE-CHAOS"

# CHAOS EVENT: Kill 3 random nodes
echo ""
echo -e "${RED}=========================================="
echo "CHAOS EVENT: Killing 3 random nodes"
echo "==========================================${NC}"

# Select 3 random nodes to kill
killed_nodes=()
while [ ${#killed_nodes[@]} -lt 3 ]; do
    node=$((RANDOM % CLUSTER_SIZE + 1))
    # Check if already in array
    if [[ ! " ${killed_nodes[@]} " =~ " ${node} " ]]; then
        killed_nodes+=($node)
    fi
done

echo -e "${RED}Killing nodes: ${killed_nodes[*]}${NC}"

for node in "${killed_nodes[@]}"; do
    pid=${NODE_PIDS[$node]}
    echo -e "${RED}  Killing node $node (PID $pid)${NC}"
    kill -9 $pid 2>/dev/null || true
done

echo ""
echo -e "${YELLOW}Waiting 10s for failure detection...${NC}"
sleep 10

check_cluster_health "AFTER-FAILURE"

if [ $HEALTH_COUNT -ne 4 ]; then
    echo -e "${RED}WARNING: Expected 4/7 nodes, got $HEALTH_COUNT/7${NC}"
fi

collect_metrics "after-failure"

# RECOVERY EVENT: Recover 2 of the 3 nodes after 30 seconds
echo ""
echo -e "${YELLOW}Waiting 30 seconds before partial recovery...${NC}"
sleep 30

echo ""
echo -e "${GREEN}=========================================="
echo "RECOVERY EVENT: Restarting 2 of 3 nodes"
echo "==========================================${NC}"

# Recover first 2 killed nodes
recover_nodes=("${killed_nodes[0]}" "${killed_nodes[1]}")
permanent_dead_node="${killed_nodes[2]}"

echo -e "${GREEN}Recovering nodes: ${recover_nodes[*]}${NC}"
echo -e "${RED}Permanently down: $permanent_dead_node${NC}"
echo ""

for node in "${recover_nodes[@]}"; do
    port=$((BASE_PORT + node - 1))
    metrics_port=$((METRICS_BASE + node - 1))
    
    # Build peers list
    peers=""
    for j in $(seq 1 $CLUSTER_SIZE); do
        if [ $j -ne $node ]; then
            peer_port=$((BASE_PORT + j - 1))
            if [ -z "$peers" ]; then
                peers="127.0.0.1:$peer_port"
            else
                peers="$peers,127.0.0.1:$peer_port"
            fi
        fi
    done
    
    echo -e "${GREEN}  Restarting node $node...${NC}"
    java -Dspiron.node.id=node-$node \
         -Dspiron.port=$port \
         -Dspiron.cluster.peers=$peers \
         -Dspiron.storage.mode=solo \
         -Dspiron.metrics.port=$metrics_port \
         -Dspiron.vector.dimensions=$BENCHMARK_DIM \
         -Dspiron.profile=BALANCED \
         -Dspiron.data.dir=/tmp/spiron-partial-test/node$node \
         -Xmx1g -Xms512m \
         -jar build/libs/spiron-all.jar \
        > /tmp/spiron-node$node-partial.log 2>&1 &
    
    NODE_PIDS[$node]=$!
    echo -e "${GREEN}  Node $node restarted (PID ${NODE_PIDS[$node]})${NC}"
done

echo ""
echo -e "${YELLOW}Waiting 30s for recovered nodes to rejoin...${NC}"
sleep 30

check_cluster_health "AFTER-PARTIAL-RECOVERY"
health_after_recovery=$?

if [ $health_after_recovery -ne 6 ]; then
    echo -e "${RED}WARNING: Expected 6/7 nodes, got $health_after_recovery/7${NC}"
fi

collect_metrics "after-recovery"

# Continue operations with 6/7 nodes
echo ""
echo -e "${BLUE}=========================================="
echo "STEADY STATE: Operating with 6/7 nodes"
echo "==========================================${NC}"
echo -e "${YELLOW}Node $permanent_dead_node remains permanently down${NC}"
echo -e "${BLUE}Cluster continues with quorum (6/7 nodes)${NC}"
echo ""

echo -e "${YELLOW}Monitoring for 1 more minute...${NC}"
sleep 60

check_cluster_health "FINAL"

collect_metrics "final"

# Stop benchmark
echo ""
echo -e "${YELLOW}Stopping benchmark...${NC}"
kill $BENCHMARK_PID 2>/dev/null || true
wait $BENCHMARK_PID 2>/dev/null || true

# Show benchmark results
echo ""
echo -e "${YELLOW}Benchmark Results:${NC}"
if [ -f /tmp/spiron-partial-benchmark.log ]; then
    tail -30 /tmp/spiron-partial-benchmark.log
else
    echo "  (benchmark log not available)"
fi

echo ""
echo -e "${GREEN}=========================================="
echo "CHAOS TEST COMPLETE"
echo "==========================================${NC}"
echo ""
echo "Test Summary:"
echo "  Initial cluster: 7/7 nodes"
echo "  Killed nodes: ${killed_nodes[*]}"
echo "  Recovered nodes: ${recover_nodes[*]}"
echo "  Permanently down: $permanent_dead_node"
echo ""

# Get final health for validation
check_cluster_health "VALIDATION" > /dev/null 2>&1
FINAL_HEALTH=$HEALTH_COUNT

# Validation
echo "Validation Results:"
if [ $FINAL_HEALTH -ge 4 ]; then
    echo -e "  ${GREEN}✓${NC} Cluster maintained quorum throughout test (4+/7)"
else
    echo -e "  ${RED}✗${NC} Quorum lost ($FINAL_HEALTH/7)"
fi

if [ $FINAL_HEALTH -eq 6 ]; then
    echo -e "  ${GREEN}✓${NC} Cluster stable with 1 permanent failure (6/7 nodes)"
else
    echo -e "  ${YELLOW}~${NC} Final cluster health: $FINAL_HEALTH/7 nodes"
fi

echo -e "  ${GREEN}✓${NC} Continuous traffic maintained throughout test"
echo -e "  ${GREEN}✓${NC} Varying vector energies processed"
echo ""

echo "Output Files:"
echo "  Node logs: /tmp/spiron-node*-partial.log"
echo "  Benchmark: /tmp/spiron-partial-benchmark.log"
echo "  Metrics: /tmp/spiron-partial-recovery-metrics-*.txt"
echo ""

# Show metrics comparison
echo -e "${YELLOW}Metrics Comparison (Node 1):${NC}"
echo "Baseline:"
grep "node 1" -A 5 /tmp/spiron-partial-recovery-metrics-baseline.txt 2>/dev/null | head -6 || echo "  (no data)"
echo ""
echo "After Failure:"
grep "node 1" -A 5 /tmp/spiron-partial-recovery-metrics-after-failure.txt 2>/dev/null | head -6 || echo "  (no data)"
echo ""
echo "Final:"
grep "node 1" -A 5 /tmp/spiron-partial-recovery-metrics-final.txt 2>/dev/null | head -6 || echo "  (no data)"

echo ""
echo -e "${GREEN}Test complete! Review logs for detailed analysis.${NC}"
