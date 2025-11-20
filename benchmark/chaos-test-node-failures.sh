#!/bin/bash
# Chaos Engineering: Node Failure Scenario Test
# Tests Spiron's resilience to node crashes and restarts

set -e

echo "=========================================="
echo "Spiron Chaos Test: Node Failures"
echo "=========================================="
echo ""

# Configuration
CLUSTER_SIZE=7
BASE_PORT=8081
METRICS_BASE=9091
TEST_DURATION_SEC=600  # 10 minutes
FAILURE_INTERVAL_SEC=60  # Kill a node every 60 seconds

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Test Configuration:${NC}"
echo "  Cluster Size: $CLUSTER_SIZE nodes"
echo "  Test Duration: ${TEST_DURATION_SEC}s"
echo "  Failure Interval: ${FAILURE_INTERVAL_SEC}s"
echo "  Scenario: Kill random node, wait 30s, restart it"
echo ""

# Node tracking
declare -a NODE_PIDS
declare -a NODE_RESTART_COUNTS

# Initialize restart counts
for i in $(seq 1 $CLUSTER_SIZE); do
    NODE_RESTART_COUNTS[$i]=0
done

# Cleanup function
cleanup() {
    echo ""
    echo -e "${YELLOW}Cleaning up...${NC}"
    
    # Stop all nodes
    echo "Stopping cluster..."
    for i in $(seq 1 $CLUSTER_SIZE); do
        pkill -f "spiron.*808$i" 2>/dev/null || true
    done
    
    echo -e "${GREEN}Cleanup complete${NC}"
}

# Set trap to cleanup on exit
trap cleanup EXIT INT TERM

# Function to start a node
start_node() {
    local node_id=$1
    local port=$((BASE_PORT + node_id - 1))
    local metrics_port=$((METRICS_BASE + node_id - 1))
    
    # Build peers list (exclude self)
    peers=""
    for j in $(seq 1 $CLUSTER_SIZE); do
        if [ $j -ne $node_id ]; then
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
        > /tmp/spiron-node$node_id-chaos.log 2>&1 &
    
    NODE_PIDS[$node_id]=$!
    echo -e "  ${GREEN}✓${NC} Node $node_id started on port $port (PID: ${NODE_PIDS[$node_id]})"
}

# Function to kill a node
kill_node() {
    local node_id=$1
    local pid=${NODE_PIDS[$node_id]}
    
    if [ -n "$pid" ] && kill -0 $pid 2>/dev/null; then
        echo -e "  ${RED}✗${NC} Killing node $node_id (PID: $pid)"
        kill -9 $pid 2>/dev/null || true
        NODE_PIDS[$node_id]=""
        return 0
    else
        echo -e "  ${YELLOW}⚠${NC} Node $node_id already dead"
        return 1
    fi
}

# Function to check if node is healthy
is_node_healthy() {
    local node_id=$1
    local metrics_port=$((METRICS_BASE + node_id - 1))
    
    curl -s http://localhost:$metrics_port/metrics > /dev/null 2>&1
    return $?
}

# Function to get cluster health
get_cluster_health() {
    local healthy=0
    for i in $(seq 1 $CLUSTER_SIZE); do
        if is_node_healthy $i; then
            healthy=$((healthy + 1))
        fi
    done
    echo $healthy
}

# Build project
echo -e "${YELLOW}Building Spiron...${NC}"
./gradlew shadowJar -x test --quiet
echo -e "${GREEN}✓ Build complete${NC}"
echo ""

# Start initial cluster
echo -e "${YELLOW}Starting 7-node cluster...${NC}"
for i in $(seq 1 $CLUSTER_SIZE); do
    start_node $i
done

echo ""
echo -e "${YELLOW}Waiting for cluster to stabilize (15s)...${NC}"
sleep 15

# Verify all nodes are running
echo -e "${YELLOW}Verifying cluster health...${NC}"
healthy=$(get_cluster_health)

if [ $healthy -ne $CLUSTER_SIZE ]; then
    echo -e "${RED}ERROR: Only $healthy/$CLUSTER_SIZE nodes are healthy${NC}"
    exit 1
fi

echo -e "${GREEN}✓ All $CLUSTER_SIZE nodes healthy${NC}"
echo ""

# Start background benchmark
echo -e "${YELLOW}Starting continuous benchmark workload...${NC}"
cd examples
java -cp build/libs/spiron-examples-*-all.jar com.spiron.BenchmarkClient \
    --target=http://localhost:8081 \
    --requests=1000000 \
    --threads=20 \
    --dimensions=4096 \
    > /tmp/spiron-chaos-node-failure-results.txt 2>&1 &

BENCHMARK_PID=$!
echo -e "${GREEN}✓ Benchmark started (PID: $BENCHMARK_PID)${NC}"
cd ..
echo ""

# Run chaos test
echo -e "${RED}=========================================="
echo "Starting Chaos: Node Failure Scenarios"
echo "==========================================${NC}"
echo ""

failures=0
recoveries=0
test_iterations=$((TEST_DURATION_SEC / FAILURE_INTERVAL_SEC))

for iteration in $(seq 1 $test_iterations); do
    echo -e "${BLUE}--- Iteration $iteration/$test_iterations ---${NC}"
    
    # Select random node to kill (avoid killing all nodes)
    healthy=$(get_cluster_health)
    if [ $healthy -le 3 ]; then
        echo -e "${YELLOW}⚠ Only $healthy nodes healthy, skipping kill to maintain quorum${NC}"
    else
        # Pick random node
        target_node=$((RANDOM % CLUSTER_SIZE + 1))
        
        echo -e "${RED}[CHAOS] Killing node $target_node...${NC}"
        if kill_node $target_node; then
            failures=$((failures + 1))
            
            # Wait to observe cluster behavior
            echo "  Waiting 30s to observe cluster behavior..."
            sleep 30
            
            # Check cluster health
            healthy=$(get_cluster_health)
            echo -e "  Cluster health: $healthy/$CLUSTER_SIZE nodes healthy"
            
            # Restart the node
            echo -e "${GREEN}[RECOVERY] Restarting node $target_node...${NC}"
            start_node $target_node
            NODE_RESTART_COUNTS[$target_node]=$((${NODE_RESTART_COUNTS[$target_node]} + 1))
            recoveries=$((recoveries + 1))
            
            # Wait for recovery
            echo "  Waiting 20s for node to rejoin cluster..."
            sleep 20
            
            # Verify recovery
            if is_node_healthy $target_node; then
                echo -e "  ${GREEN}✓ Node $target_node recovered successfully${NC}"
            else
                echo -e "  ${RED}✗ Node $target_node failed to recover${NC}"
            fi
        fi
    fi
    
    # Get current cluster health
    healthy=$(get_cluster_health)
    echo -e "${BLUE}Current cluster health: $healthy/$CLUSTER_SIZE nodes${NC}"
    
    # Collect quick metrics
    echo "Quick metrics check:"
    for i in $(seq 1 $CLUSTER_SIZE); do
        if is_node_healthy $i; then
            metrics_port=$((METRICS_BASE + i - 1))
            commits=$(curl -s http://localhost:$metrics_port/metrics | grep "spiron_crdt_commits_total" | awk '{print $2}')
            failures=$(curl -s http://localhost:$metrics_port/metrics | grep "spiron_rpc_failures_total" | awk '{print $2}')
            echo -e "  Node $i: commits=${commits:-0}, failures=${failures:-0}"
        else
            echo -e "  Node $i: ${RED}DOWN${NC}"
        fi
    done
    
    echo ""
    
    # If not last iteration, wait remaining time
    if [ $iteration -lt $test_iterations ]; then
        remaining=$((FAILURE_INTERVAL_SEC - 50))  # 30s wait + 20s recovery = 50s
        if [ $remaining -gt 0 ]; then
            echo "Waiting ${remaining}s before next failure..."
            sleep $remaining
        fi
    fi
done

echo ""
echo -e "${GREEN}Chaos test complete!${NC}"
echo ""

# Stop benchmark
if kill -0 $BENCHMARK_PID 2>/dev/null; then
    echo "Stopping benchmark..."
    kill $BENCHMARK_PID 2>/dev/null || true
    wait $BENCHMARK_PID 2>/dev/null || true
fi

# Final health check
echo -e "${YELLOW}Final Cluster Health Check:${NC}"
healthy=$(get_cluster_health)
echo -e "  Healthy nodes: $healthy/$CLUSTER_SIZE"
echo ""

# Collect detailed metrics
echo -e "${YELLOW}Collecting Final Metrics...${NC}"
echo ""

for i in $(seq 1 $CLUSTER_SIZE); do
    if is_node_healthy $i; then
        metrics_port=$((METRICS_BASE + i - 1))
        echo "Node $i (Restarts: ${NODE_RESTART_COUNTS[$i]}):"
        curl -s http://localhost:$metrics_port/metrics | grep -E "spiron_(crdt_commits_total|rpc_commit_total|rpc_failures_total)" | head -10
        echo ""
    else
        echo -e "Node $i: ${RED}DOWN${NC} (Restarts: ${NODE_RESTART_COUNTS[$i]})"
        echo ""
    fi
done

# Summary
echo -e "${BLUE}=========================================="
echo "Test Summary"
echo "==========================================${NC}"
echo "  Total node failures: $failures"
echo "  Total recoveries: $recoveries"
echo "  Final cluster health: $healthy/$CLUSTER_SIZE nodes"
echo ""

# Node restart breakdown
echo "Node restart counts:"
for i in $(seq 1 $CLUSTER_SIZE); do
    echo "  Node $i: ${NODE_RESTART_COUNTS[$i]} restarts"
done
echo ""

# Show benchmark results
if [ -f /tmp/spiron-chaos-node-failure-results.txt ]; then
    echo -e "${YELLOW}Benchmark Results:${NC}"
    tail -50 /tmp/spiron-chaos-node-failure-results.txt
    echo ""
fi

echo -e "${GREEN}=========================================="
echo "Chaos Test Complete: Node Failures"
echo "==========================================${NC}"
echo ""
echo "Review logs in /tmp/spiron-node*-chaos.log"
echo "Benchmark results in /tmp/spiron-chaos-node-failure-results.txt"
