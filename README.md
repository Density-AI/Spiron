# Spiron — The AI/LLM-First Consensus Engine

> **Spiron** is the first **LLM/AI/ML-native consensus engine**, designed to help distributed intelligent systems agree on *meaning*, *intent*, or *state vectors* — not just bytes or log entries.

Traditional consensus systems like **Raft**, **Paxos**, or **Ratis** ensure all nodes agree on *what happened*.  
**Spiron** helps intelligent nodes agree on *what matters most*.

## Architecture & Modules

Spiron is a single **multi-module Gradle project** with a shared core and separate server and client modules:

- **Root project (`.`)** – Shared core API and engine
  - `com.spiron.core.*` – Eddy engine, math, lineage-aware `EddyState`
  - `com.spiron.config.*` – `SpironConfig`, profiles, and validation
  - `com.spiron.api.*` – Public Java API (`SpironClient`, `SpironServer` wrapper)
- **`spiron-server/`** – Server runtime
  - Depends on the root core API
  - Provides gRPC `RpcServer`, CRDT stores, snapshots, metrics, DI, etc.
  - Produces a fat jar: `spiron-server/build/libs/spiron-server-all.jar`
- **`spiron-clients/spiron-java-client/`** – Java client SDK
  - Depends on the root core API and `spiron-server` for RPC/DI types
  - Exposes a simplified `SpironClient` and example apps
- **`spiron-clients/spiron-python-client/`** – Python client SDK with async support

This modular architecture enables:
- Centralized dependency and version management via the root `build.gradle`
- Independent client/server and language-specific releases
- Shared semantics (lineage, metrics, config) across server and clients
- Easier testing, refactoring, and reuse between modules

---

## 🎯 What is Spiron?

Spiron introduces a new primitive called an **Eddy**:

```java
public record EddyState(String id, double[] vector, double energy, String parentId)
  implements Serializable
```

Each eddy represents a **belief**, **signal**, or **proposal** — expressed as:

* **id:** Unique origin or agent name
* **vector:** High-dimensional embedding (128–8192 dimensions)
* **energy:** Intensity/confidence/priority of the signal
* **parentId:** Lineage pointer to the eddy this one was merged from (used for causal tracing and auditability)

Spiron continuously merges similar eddies and dampens divergent ones until a **dominant eddy** emerges whose energy exceeds a threshold — at which point it is **committed** cluster-wide.

---

## ⚙️ Core Architecture

### Components Overview

// See docs for architecture diagram

### Key Components

#### Core Layer
* **EddyEngine** – Merges eddies using angular similarity, siphons energy, detects dominance
* **EddyMath** – Mathematical primitives (angular similarity, normalization, dot product)
* **EddyState** – Immutable record (id, vector, energy)

#### Network Layer
* **RpcServer** – gRPC server with Broadcast, Commit, Sync endpoints
* **RpcClient** – gRPC client for peer communication
* **EddyGossipService** – CRDT-based state synchronization

#### Storage Layer
1. **CRDTStore** – Distributed key-value storage
   - RocksDbCRDTStore (solo mode, high performance)
   - EtcdCRDTStore (cluster mode, distributed consistency)
2. **SpironRaftLog** – Append-only commit log
3. **SpironSnapshotStore** – Point-in-time state snapshots

#### Security & Config
* **BlsSigner** – BLS12-381 signatures (Tuweni)
* **SpironConfig** – Configuration with 4 performance profiles
* **PerformanceMetrics** – Prometheus metrics (latency, throughput, energy)

---

## 🧮 Mathematical Core

### 1. Angular Similarity

Similarity between vectors a and b:

```
sim(a, b) = (a · b) / (|a| |b|)
```

### 2. Energy Siphon

Energy transfer from similar eddies:

```
E_new = E_a + (siphonFactor) × sim(a, b) × E_b
```

### 3. Damping

Dissimilar vectors are damped:

```
E_new = alpha × E_a
```

### 4. Dominance

Eddy commits when:

```
E_dominant >= E_commitThreshold
```

---

## 🚀 Quick Start

### Prerequisites

* **Java 17+**
* **Gradle 8.0+** (wrapper included)

### Build (Multi-Module)

```bash
git clone https://github.com/Density-AI/spiron.git
cd spiron

# Full build + tests for all modules
./gradlew clean build

# Or build specific modules
./gradlew :spiron-server:build
./gradlew :spiron-clients:spiron-java-client:build
```

### Run Server (Solo Mode)

The server module produces both a slim jar and a fat jar. For local runs and benchmarks, use the fat jar:

```bash
./gradlew :spiron-server:shadowJar
java -jar spiron-server/build/libs/spiron-server-all.jar
```

This starts a node using `src/main/resources/application.properties`:
- Node ID: `node-0`
- Port: `8080`
- Metrics: `http://localhost:9090/metrics`
- Storage: `/tmp/spiron`

### Run Cluster Mode (3 Nodes)

Terminal 1:
```bash
./gradlew :spiron-server:shadowJar
java -jar spiron-server/build/libs/spiron-server-all.jar \
  --spiron.node.id=node-1 \
  --spiron.port=8081 \
  --spiron.cluster.peers=localhost:8081,localhost:8082,localhost:8083 \
  --spiron.storage.mode=solo
```

Terminal 2:
```bash
java -jar spiron-server/build/libs/spiron-server-all.jar \
  --spiron.node.id=node-2 \
  --spiron.port=8082 \
  --spiron.cluster.peers=localhost:8081,localhost:8082,localhost:8083 \
  --spiron.storage.mode=solo
```

Terminal 3:
```bash
java -jar spiron-server/build/libs/spiron-server-all.jar \
  --spiron.node.id=node-3 \
  --spiron.port=8083 \
  --spiron.cluster.peers=localhost:8081,localhost:8082,localhost:8083 \
  --spiron.storage.mode=solo
```

---

## 💻 Client API Usage

### Java Client

The Java client lives in the `spiron-clients/spiron-java-client` module and uses the shared core types (`EddyState`, `SpironConfig`) and `RpcClient` from the root project.

```java
import com.spiron.client.SpironClient;
import com.spiron.core.EddyState;

// Create client
var client = new SpironClient.Builder()
  .peers(List.of("localhost:8081", "localhost:8082", "localhost:8083"))
  .workerThreads(4)
  .build();

// Propose an eddy (synchronous)
double[] vector = {0.9, 0.1, 0.0};
EddyState proposal = new EddyState("proposal-1", vector, 1.5, null);
client.propose(proposal);

// Close when done
client.close();
```

**Build Java client:**

```bash
cd spiron-clients/spiron-java-client
./gradlew build
```

### Python Client

```python
from spiron import SpironClient, EddyState

# Create client
client = SpironClient(
    peers=["localhost:8081", "localhost:8082", "localhost:8083"],
    worker_threads=4
)

# Propose an eddy (async)
vector = [0.9, 0.1, 0.0]
eddy = EddyState("proposal-1", vector, 1.5)
await client.propose_async(eddy)

# Or synchronous
client.propose(eddy)

# Close when done
client.close()
```

**Installation:**
```bash
cd spiron-clients/spiron-python-client
pip install -e .
```

### Running Examples

**Java:**
```bash
cd spiron-clients/spiron-java-client
./gradlew run --args="localhost:8081"
```

**Python:**
```bash
cd spiron-clients/spiron-python-client
python examples/basic_example.py localhost:8081
python examples/benchmark_client.py 10000 4096 20
```

### Embedded Server

```java
import com.spiron.api.SpironServer;
import com.spiron.config.SpironConfig;
import com.spiron.di.DaggerSpironComponent;
import com.spiron.security.BlsSigner;

// Load configuration
SpironConfig config = SpironConfig.load();

// Create BLS signer
BlsSigner signer = BlsSigner.fromKeystore(
    BlsSigner.getKeystoreDir(config.dataDir()), 
    config.nodeId()
);

// Build component with Dagger
var component = DaggerSpironComponent.builder()
    .config(config)
    .blsSigner(signer)
    .build();

// Start server
SpironServer server = new SpironServer(
    component.rpcServer(), 
    component.engine()
);
new Thread(server).start();
```

**Or run standalone (fat jar):**
```bash
./gradlew :spiron-server:shadowJar
java -jar spiron-server/build/libs/spiron-server-all.jar
```

---

## ⚙️ Configuration

### Performance Profiles

Spiron includes 4 pre-tuned profiles:

| Profile      | Use Case                    | Alpha | Siphon | Threshold | Commit | Peers |
|--------------|----------------------------|-------|--------|-----------|--------|-------|
| LOW_LATENCY  | Fast convergence           | 0.98  | 0.3    | 0.85      | 2.0    | 3     |
| MIN_QUORUM   | Minimal quorum             | 0.95  | 0.25   | 0.75      | 2.5    | 2     |
| MAX_QUORUM   | Maximum fault tolerance    | 0.92  | 0.15   | 0.65      | 3.5    | 7     |
| BALANCED     | General purpose (default)  | 0.95  | 0.2    | 0.7       | 2.5    | 5     |

Set in `application.properties`:
```properties
spiron.profile=BALANCED
```

### Key Parameters

| Parameter                  | Description                          | Default        |
|---------------------------|--------------------------------------|----------------|
| spiron.node.id            | Unique node identifier               | node-0         |
| spiron.port               | gRPC server port                     | 8080           |
| spiron.vector.dimensions  | Vector size (128-2000)              | 128            |
| spiron.profile            | Performance profile                  | BALANCED       |
| spiron.cluster.mode       | solo or cluster                      | solo           |
| spiron.cluster.peers      | Comma-separated peer list            | (empty)        |
| spiron.storage.mode       | solo (RocksDB) or cluster (etcd)    | solo           |
| spiron.data.dir           | Persistence directory                | /tmp/spiron    |
| spiron.metrics.port       | Prometheus metrics port              | 9090           |
| spiron.etcd.endpoints     | etcd cluster endpoints               | (empty)        |

### Environment Variables

Override any property with environment variables:
```bash
export SPIRON_NODE_ID=node-1
export SPIRON_PORT=8081
```

---

## 🔐 BLS Signatures

Every commit is signed with BLS12-381:

```proto
message CommitEnvelope {
  CommitBody body = 1;
  bytes bls_pubkey = 2;
  bytes bls_signature = 3;
  string sig_scheme = 10;
}
```

Verification ensures tamper-proof commits across the cluster.

---

## 📊 Metrics & Observability

### Prometheus Metrics

Access at `http://localhost:9090/metrics`:

- `spiron_rpc_broadcast_total{peer}` – Broadcast count per peer
- `spiron_rpc_commit_total{peer}` – Commit count per peer
- `spiron_rpc_failures_total` – RPC failures
- `spiron_rpc_latency` – RPC latency histogram (P50, P90, P99)
- `spiron_merges_total` – Merge operations
- `spiron_energy_levels` – Energy distribution
- `spiron_eddy_energy{id}` – Per-eddy energy gauge

### Grafana Dashboard

Import `docs/grafana/spiron_dashboard.json` for pre-built visualizations.

---

## 🐳 Docker

### Build Image

```bash
cd spiron-server
../gradlew :spiron-server:shadowJar
docker build -t spiron:latest .
```

### Run Container

```bash
docker run -p 8080:8080 -p 9090:9090 \
  -e SPIRON_NODE_ID=node-1 \
  -e SPIRON_PORT=8080 \
  spiron:latest
```

---

## ☸️ Kubernetes

### Deploy StatefulSet

```bash
kubectl apply -f k8s/spiron-service.yaml
kubectl apply -f k8s/spiron-statefulset.yaml
```

Each pod gets:
- Unique node ID from pod name
- Persistent volume for storage
- Service discovery via DNS

---

## 📚 Documentation

### Interactive Diagrams
- **[Class Diagram](docs/class-diagram.html)** – Complete UML class diagram (50+ components)
- **[Architecture Class Diagram](docs/architecture-class-diagram.html)** – Full system architecture with metrics
- **[Sequence Diagrams](docs/sequence-diagram.html)** – Data flow visualizations
- **[Data Flow Diagrams](docs/data-flow-sequence-diagrams.html)** – 8 comprehensive sequence diagrams

### Guides
- **[Complete Architecture 2025](docs/COMPLETE_ARCHITECTURE_2025.md)** – ⭐ Comprehensive architecture documentation
- **[Implementation Details](docs/IMPLEMENTATIONS_COMPLETED.md)** – Storage backend implementations
- **[Cleanup Summary](docs/CLEANUP_SUMMARY.md)** – Code quality improvements
- **[Metrics Guide](docs/metrics.md)** – Detailed metrics documentation (60+ metrics)

View diagrams:
```bash
open docs/class-diagram.html
open docs/architecture-class-diagram.html
open docs/sequence-diagram.html
open docs/data-flow-sequence-diagrams.html
```

---

## 🧪 Testing

### Run All Tests (All Modules)

```bash
./gradlew test
```

**Test Coverage:** 136 tests across:
- **Validation Layer (34 tests)** ⭐ NEW
  - BroadcastValidator (11 tests)
  - DuplicateDetector (7 tests)
  - RateLimiter (9 tests)
  - PeerAllowlist (6 tests)
  - Crash recovery (1 test)
- **CRDT & Finality (21 tests)** ⭐ NEW
  - CRDTMergeEngine (10 tests)
  - ApprovalCounter (8 tests)
  - FinallityDetector (3 tests)
- **Configuration profiles (17 tests)**
- **Vector dimensions (15 tests)**
- **Performance metrics (16 tests)**
- **Integration tests (54 tests)**

### Run with Coverage

```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

---

## 🧠 Use Cases

| Domain                   | Example                                    | Spiron Role                                   |
|--------------------------|-------------------------------------------|-------------------------------------------------|
| **Multi-Agent AI**       | LLM agents propose next actions           | Merges semantic embeddings into consensus       |
| **Recommender Systems**  | Trending detection from user behavior     | Aggregates interest vectors                     |
| **IoT & Edge**           | Multi-sensor fusion                       | Finds representative environmental vector       |
| **Autonomous Swarms**    | Robots share trajectories                 | Emergent shared direction                       |
| **Federated ML**         | Distributed gradient updates              | Semantic federated averaging                    |
| **Anomaly Detection**    | Microservices report metrics              | High-energy eddy = anomaly consensus            |

---

## 🔬 Comparison

| Feature              | Raft      | Paxos     | FedAvg       | Spiron                      |
|---------------------|-----------|-----------|--------------|------------------------------|
| Consensus Target    | Bytes     | Bytes     | Gradients    | Vectors/embeddings           |
| Leadership          | Elected   | Elected   | Central      | Emergent (energy-based)      |
| Fault Model         | Crash     | Crash     | Partial      | Probabilistic                |
| Deterministic       | Yes       | Yes       | No           | No (continuous)              |
| Decentralized       | Partial   | Partial   | No           | Yes                          |
| Crypto Signatures   | Optional  | No        | No           | ✅ BLS12-381                 |
| Use Case            | Databases | Coord.    | Fed. Learning| AI consensus                 |

---

## 🏗️ Project Structure

```
spiron/
├── spiron-server/                    # Server implementation
│   ├── src/main/java/com/spiron/
│   │   ├── config/           # Configuration (SpironConfig, PerformanceProfile)
│   │   ├── core/             # Consensus engine (EddyEngine, EddyMath)
│   │   ├── crdt/             # CRDT merge logic
│   │   ├── di/               # Dependency injection (Dagger)
│   │   ├── metrics/          # Metrics (PerformanceMetrics, MetricsRegistry)
│   │   ├── network/          # gRPC (RpcServer, RpcClient, EddyGossipService)
│   │   ├── security/         # Cryptography (BlsSigner)
│   │   ├── serialization/    # Codecs (CRDTJsonCodec)
│   │   ├── storage/          # Storage (CRDTStore, RocksDb, etcd, RaftLog)
│   │   └── App.java          # Server entrypoint
│   ├── src/main/proto/       # Protobuf definitions
│   ├── src/main/resources/   # application.properties
│   ├── src/test/java/        # 136+ unit and integration tests
│   └── build.gradle
│
├── spiron-clients/
│   ├── spiron-java-client/          # Java client library
│   │   ├── src/main/java/com/spiron/client/
│   │   │   ├── SpironClient.java    # Main Java client
│   │   │   ├── EddyState.java       # Eddy state class
│   │   │   └── examples/
│   │   │       ├── BenchmarkClient.java
│   │   │       └── SimpleTest.java
│   │   ├── src/main/proto/          # Protobuf definitions
│   │   └── build.gradle
│   │
│   └── spiron-python-client/        # Python client library
│       ├── spiron/
│       │   ├── client.py            # Main Python client
│       │   ├── eddy.py              # EddyState dataclass
│       │   └── exceptions.py
│       ├── examples/
│       │   ├── basic_example.py
│       │   ├── async_example.py
│       │   └── benchmark_client.py
│       ├── tests/                   # Pytest test suite
│       └── pyproject.toml
│
├── benchmark/                       # Chaos testing and benchmarks
│   ├── chaos-test-node-failures.sh
│   ├── chaos-test-partial-recovery.sh
│   ├── long-running-stability.sh
│   ├── quick-stability-test.sh
│   ├── run-benchmark.sh
│   ├── run-real-chaos-test.sh
│   ├── start-7node-cluster.sh
│   └── start-7node-4096d.sh
│
├── docs/                            # Documentation and diagrams
└── k8s/                             # Kubernetes manifests
```

---

## 🛠️ Build from Source

### Requirements

- Java 17+
- Gradle 8.0+
- Python 3.8+ (for Python client)

### Build Steps

```bash
# Clone repository
git clone https://github.com/Density-AI/spiron.git
cd spiron

# Build server
cd spiron-server
./gradlew clean build shadowJar

# Build Java client
cd ../spiron-clients/spiron-java-client
./gradlew build

# Install Python client
cd ../spiron-python-client
pip install -e .

# Run tests
cd ../../spiron-server
./gradlew test
```

### Outputs

- Server: `spiron-server/build/libs/spiron-server.jar`
- Java Client: `spiron-clients/spiron-java-client/build/libs/spiron-java-client-0.1.0.jar`
- Python Client: Installed via pip

---

## 🔧 Development

### Run Dependency Check

```bash
./gradlew dependencyCheckAnalyze
open build/reports/dependency-check/dependency-check-report.html
```

### Run Linter

```bash
./gradlew check
```

### Generate Javadoc

```bash
./gradlew javadoc
open build/docs/javadoc/index.html
```

---

## 📖 Example: Three-Node Consensus

1. **Start Node A:**
```bash
cd spiron-server
java -jar build/libs/spiron-server.jar \
  --spiron.node.id=node-A \
  --spiron.port=8081 \
  --spiron.cluster.peers=localhost:8081,localhost:8082,localhost:8083 \
  --spiron.profile=LOW_LATENCY
```

2. **Start Node B & C** (similar with ports 8082, 8083)

3. **Propose from Java Client:**
```java
var client = new SpironClient.Builder()
    .peers(List.of("localhost:8081"))
    .build();

double[] vec = {0.9, 0.1, 0.0};
client.proposeAsync(new EddyState("plan-1", vec, 1.2)).join();
```

4. **Or from Python Client:**
```python
client = SpironClient(peers=["localhost:8081"])
eddy = EddyState("plan-1", [0.9, 0.1, 0.0], 1.2)
client.propose(eddy)
```

5. **Watch Consensus:**
- Metrics: `http://localhost:9090/metrics`
- Each node ingests, merges similar vectors
- Energy siphons when similarity > threshold
- Dominant eddy commits when energy >= 2.0

6. **Verify Commit:**
```bash
curl http://localhost:9090/metrics | grep spiron_commit_total
```

---

## 📊 Performance & Benchmarks

### Benchmark Results

Spiron has been extensively benchmarked with **100,000 high-dimensional vectors** across 7-node clusters:

| Configuration | Throughput | Data Rate | Compression | Reliability |
|--------------|------------|-----------|-------------|-------------|
| **100K @ 4096D** | 22,232 req/sec | 695 MB/sec | 7,971:1 | 100% (0 failures) |
| **100K @ 128D** | 34,626 req/sec | 34 MB/sec | 3,164:1 | 100% (0 failures) |

**Key Achievements:**
- ✅ **Zero failures** across all benchmarks
- ✅ **Sub-millisecond P99 latency** for client operations  
- ✅ **~8000:1 CRDT compression** (3.125 GB → 392 KB)
- ✅ **Perfect load balancing** (±0.3% variance)

### Running Benchmarks

```bash
# Start 7-node cluster
./benchmark/start-7node-cluster.sh
sleep 10

# Run Java benchmark
cd spiron-clients/spiron-java-client
./gradlew run -PmainClass=com.spiron.client.examples.BenchmarkClient --args="7 100000"

# Or run Python benchmark
cd ../spiron-python-client
python examples/benchmark_client.py 100000 4096 20
```

**Chaos testing:**
```bash
# Node failures test
./benchmark/chaos-test-node-failures.sh

# Partial recovery test
./benchmark/chaos-test-partial-recovery.sh

# Long-running stability test
./benchmark/long-running-stability.sh
```

**Full benchmark reports:** See [docs/benchmarks/](docs/benchmarks/)

---

## 📊 Architecture Diagrams

Comprehensive visual documentation of Spiron's architecture and data flows:

### 🎨 Interactive Diagrams (Mermaid.js)

1. **[Complete Architecture Class Diagram](docs/architecture-class-diagram.html)**
   - All 40+ classes with relationships
   - Complete metrics instrumentation (53+ metrics)
   - CRDT layer, storage backends, network layer
   - Color-coded components with interactive navigation

2. **[Complete Data Flow Sequence Diagrams](docs/data-flow-sequence-diagrams.html)**
   - 8 comprehensive sequence diagrams covering:
     - Eddy broadcast with metrics tracking
     - CRDT merge operations (LWW + G-Counter)
     - Gossip synchronization flow
     - BLS signature commit flow
     - Storage backend selection
     - Metrics collection & background updates
     - Cluster startup sequence
     - Consensus convergence

### 📖 How to View
```bash
# Open in browser
open docs/architecture-class-diagram.html
open docs/data-flow-sequence-diagrams.html

# Or via local server
cd docs && python3 -m http.server 8000
# Visit http://localhost:8000/architecture-class-diagram.html
```

**Documentation:** See [docs/DIAGRAMS.md](docs/DIAGRAMS.md) for complete diagram guide.

---

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 📄 License

```
Copyright (c) 2025 Density AI
Licensed under the Apache License, Version 2.0
```

See [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

Built with:
- **gRPC** – Network communication
- **Dagger** – Dependency injection
- **RocksDB** – Embedded storage
- **etcd** – Distributed key-value store
- **Tuweni** – BLS12-381 cryptography
- **Micrometer** – Metrics
- **JUnit** – Testing

---

## 📚 Citations

* "Self-Consistency Improves Chain-of-Thought" – Google, 2022
* "LLM Agents as Game Players" – Stanford, 2023
* "Decentralized Federated Averaging" – OpenAI/Meta, 2024

---

## ☕ Support

If you find Spiron useful:

[![Buy Me A Coffee](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/densityai)

---

**Built with ❤️ by Density AI**
