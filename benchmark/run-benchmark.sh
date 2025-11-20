#!/bin/bash
# Spiron Benchmark Runner
# Automates starting a 3-node cluster and running performance benchmarks

set -e

SPIRON_DIR="/Users/n0j04yp/Documents/personal/stormen-alpha/spiron"
cd "$SPIRON_DIR"

echo "╔══════════════════════════════════════════════════════════╗"
echo "║         Spiron Automated Benchmark Runner               ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo

# Step 1: Build
echo "📦 Step 1/4: Building Spiron..."
./gradlew clean :examples:classes shadowJar -x test > /dev/null 2>&1
echo "  ✓ Build complete"
echo

# Step 2: Start cluster
echo "🚀 Step 2/4: Starting 3-node cluster..."
./start-cluster.sh
echo

# Step 3: Run benchmark
echo "⚡ Step 3/4: Running benchmark (this may take ~30 seconds)..."
sleep 2  # Give nodes time to stabilize
java -cp "build/libs/spiron-all.jar" com.spiron.BenchmarkClient > benchmark-output.log 2>&1
echo "  ✓ Benchmark complete"
echo

# Step 4: Display results
echo "📊 Step 4/4: Results"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cat benchmark-summary.txt
echo
echo "Full details:"
echo "  - Summary: docs/benchmarks/benchmark-summary.txt"
echo "  - Full report: docs/benchmarks/BENCHMARK_RESULTS.md"
echo "  - Raw output: benchmark/benchmark-output.log"
echo "  - Node logs: node1.log, node2.log, node3.log"
echo
echo "Cluster still running. To stop:"
echo "  pkill -f spiron-all.jar"
echo
