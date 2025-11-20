# Spiron Benchmark Scripts

This directory contains scripts for running performance benchmarks and starting test clusters.

## 📁 Directory Structure

```
benchmark/
├── README.md                    # This file
├── start-cluster.sh            # Start 3-node test cluster
├── start-7node-cluster.sh      # Start 7-node cluster (128D)
├── start-7node-4096d.sh        # Start 7-node cluster (4096D)
├── run-benchmark.sh            # Automated benchmark runner
├── run_cli_demo.sh             # CLI demo script
├── run-node1.sh               # Individual node startup scripts
├── run-node2.sh
├── run-node3.sh
└── benchmark-100k-output.log   # Latest benchmark output
```

## 🚀 Quick Start

### Run Automated Benchmark
```bash
cd /path/to/spiron
./benchmark/run-benchmark.sh
```

### Start 7-Node Cluster (4096D)
```bash
./benchmark/start-7node-4096d.sh
```

### Run Manual Benchmark
```bash
# 1. Start cluster
./benchmark/start-7node-4096d.sh

# 2. Wait for nodes to initialize (10s)
sleep 10

# 3. Run benchmark
cd examples
java -cp ../build/libs/spiron-all.jar com.spiron.BenchmarkClient
```

## 📊 Benchmark Results

All benchmark documentation and results are in `docs/benchmarks/`:
- `BENCHMARK_RESULTS.md` - General benchmark results
- `BENCHMARK_100K_7NODES.md` - 100K vectors @ 128D (7 nodes)
- `BENCHMARK_100K_4096D_7NODES.md` - 100K vectors @ 4096D (7 nodes)
- `benchmark-summary.txt` - Latest benchmark summary

## 🛑 Stop Cluster

```bash
pkill -f spiron-all.jar
```

## 📝 Notes

- All scripts assume you're in the Spiron root directory
- Nodes log to `node1.log`, `node2.log`, etc. in the root directory
- Metrics are exposed on ports 9091-9097
- RPC servers run on ports 8081-8087

## 🧪 Production Hardening & Chaos Scripts

Spiron includes scripts for advanced production hardening and chaos testing:

### 1. Network Latency Injection
Simulate network delays and packet loss to test resilience.
```bash
./benchmark/chaos-test-network-latency.sh
```
- Injects latency and jitter on all cluster nodes
- Monitors cluster health and benchmark results

### 2. Node Failure Scenarios
Randomly kills and restarts nodes to test recovery and quorum.
```bash
./benchmark/chaos-test-node-failures.sh
```
- Kills a random node every 60s, restarts after 30s
- Runs continuous benchmark workload
- Tracks node restarts and cluster health

### 3. Partition Tolerance Testing
Simulate network partitions (Linux only) to test split-brain handling.
```bash
./benchmark/chaos-test-partition.sh
```
- Partitions a random node from the cluster for 60s
- Heals partition and observes recovery

### 4. Long-Running Stability (24h+)
Run a 24-hour soak test with continuous benchmarking.
```bash
./benchmark/long-running-stability.sh
```
- Runs cluster and benchmark for 24 hours
- Collects hourly metrics snapshots
- Verifies stability and absence of memory/resource leaks

---

**All scripts log to `/tmp/spiron-node*-chaos.log` and output benchmark results for analysis.**

> For best results, run chaos scripts on Linux. macOS support is limited for network shaping.
