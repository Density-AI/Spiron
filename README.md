# 🌪️ Spiron — The First AI/LLM-First Consensus Engine

> **Spiron** is first **LLM/AI/ML-native consensus engine**, designed to help distributed intelligent systems agree on *meaning*, *intent*, or *state vectors* — not just bytes or log entries.

Traditional consensus systems like **Raft**, **Paxos**, or **Ratis** ensure all nodes agree on *what happened*.
**Spiron** helps intelligent nodes agree on *what matters most*.


---

Spiron introduces a new primitive called an **Eddy**:

```proto
message EddyStateMsg {
  string id = 1;
  repeated double vector = 2;
  double energy = 3;
}
```

Each eddy represents a **belief**, **signal**, or **proposal** — expressed as:

* **id:** the unique origin or agent name
* **vector:** a high-dimensional embedding or numeric state
* **energy:** the intensity/confidence/priority of the signal

Spiron continuously merges similar eddies and dampens divergent ones until a **dominant eddy** emerges whose energy exceeds a threshold — at which point it is **committed** cluster-wide.

---

## ⚙️ Core Architecture

### Components

* **EddyEngine** – performs angular similarity merging, damping, and dominance detection
* **RpcServer / RpcClient** – exposes gRPC API for `Broadcast`, `Commit`, and future query operations
* **BlsSigner** – cryptographic signing & verification (BLS12-381 via Tuweni)
* **SpironRaftLog** – optional durable append-only store for committed states
* **SpironSnapshotStore** – maintains snapshot of current dominant states
* **Dagger DI Module** – production-grade dependency injection
* **Prometheus Micrometer metrics** – for observability

---

## 📊 Data Flow Overview

```text
                ┌────────────┐
                │  Agent/LLM │
                │   proposes │
                │(id, vec, E)│
                └──────┬─────┘
                       │  Broadcast()
                       ▼
               ┌────────────────────┐
               │     RpcServer      │
               │   receives Eddy    │
               └──────┬─────────────┘
                      │
                      ▼
               ┌────────────────────┐
               │    EddyEngine      │
               │  • merge eddies    │
               │  • siphon energy   │
               │  • detect dominant │
               └──────┬─────────────┘
                      │ if dominant
                      ▼
               ┌────────────────────┐
               │   BlsSigner        │
               │ signs CommitBody   │
               └──────┬─────────────┘
                      │
                      ▼
               ┌────────────────────┐
               │   SpironRaftLog    │
               │   append + snapshot│
               └────────────────────┘
```

---

## 🧮 Mathematical Core

Spiron’s merge behavior is governed by **angular similarity** and **energy transfer**. Formulas are presented in standard scientific notation using LaTeX style.

### 1. Angular Similarity

The similarity between two eddy vectors ( \vec{a} ) and ( \vec{b} ) is defined as:

$$
\text{sim}(\vec{a}, \vec{b}) = \frac{\vec{a} \cdot \vec{b}}{|\vec{a}| |\vec{b}|}
$$

### 2. Siphon Energy Transfer

Energy is partially transferred from one eddy to another when their vectors are similar:

$$
E_{\text{new}} = E_a + (\text{siphonFactor}) \times \text{sim}(\vec{a}, \vec{b}) \times E_b
$$

### 3. Damping (for dissimilar vectors)

When vectors are dissimilar (below the angular threshold):

$$
E_{\text{new}} = \alpha \times E_a
$$

### 4. Dominance Detection

An eddy is considered dominant and committed when:

$$
E_{\text{dominant}} \geq E_{\text{commitThreshold}}
$$

---

## ⚙️ Configurable Parameters

| Parameter          | Meaning                                            | Typical Range |
| ------------------ | -------------------------------------------------- | ------------- |
| `alpha`            | Damping factor for divergent eddies                | 0.90 – 0.99   |
| `siphonFactor`     | Fraction of energy transferred from similar eddies | 0.1 – 0.5     |
| `angularThreshold` | Minimum cosine similarity for merging              | 0.6 – 0.9     |
| `commitEnergy`     | Threshold at which an eddy is committed            | 2.0 – 5.0     |
| `dataDir`          | Persistence path                                   | Any local dir |
| `peers`            | Comma-separated list of peer addresses             | host:port,... |

---

## 🔐 BLS-Signed Commit Envelopes

Every commit is wrapped in a **BLS signature envelope**:

```proto
message CommitEnvelope {
  CommitBody body = 1;
  bytes bls_pubkey = 2;
  bytes bls_signature = 3;
  string sig_scheme = 10;
}
```

Mathematically:

$$
\text{sig} = \text{BLS.sign}(H(\text{body}), sk)
$$

$$
\text{verify} = \text{BLS.verify}(\text{sig}, pk, H(\text{body}))
$$

This ensures verifiable, tamper-proof commits across the cluster.

---

## 💻 Installation

### Prerequisites

* **Java 25+**
* **Gradle 9+**
* **Protobuf compiler (installed via plugin automatically)**

### Clone and Build

```bash
git clone https://github.com/spiron-ai/spiron.git
cd spiron
./gradlew clean build shadowJar
```

### Run spiron Node

```bash
java -jar build/libs/spiron.jar \
  --id=node-1 \
  --port=8081 \
  --peers=localhost:8081,localhost:8082 \
  --alpha=0.98 \
  --siphon=0.2 \
  --theta=0.6 \
  --commitEnergy=2.5 \
  --dataDir=/var/lib/spiron
```

### Add as a library (Gradle / Maven)

If you want to depend on Spiron as a library, publish locally and consume from your project.

Build and publish to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then add as a dependency:

Gradle (Kotlin/Groovy):

```gradle
implementation 'com.spiron:spiron:0.1.0'
```

Maven:

```xml
<dependency>
       <groupId>com.spiron</groupId>
       <artifactId>spiron</artifactId>
       <version>0.1.0</version>
</dependency>
```

Note: this project publishes to mavenLocal by default in the Gradle `publishing` configuration.

### Docker

Build the Docker image (uses the included `Dockerfile`):

```bash
docker build -t your-registry/spiron:latest .
```

Run a node with the recommended environment variables (Spiron reads some values from env vars when present):

```bash
docker run --rm -p 8080:8080 -p 9090:9090 \
       -e spiron_NODE_ID=node-1 \
       -e spiron_PORT=8080 \
       your-registry/spiron:latest
```

If you need to pass configuration overrides to the CLI, use the `start-server` command in the jar or pass `--key=value` arguments:

```bash
java -cp build/libs/spiron.jar com.spiron.tools.SpironCli start-server --spiron.node.id=node-1 --spiron.port=8080 --spiron.cluster.peers=localhost:8080
```

### Kubernetes

A small example `k8s/` manifests are provided (`spiron-service.yaml` and `spiron-statefulset.yaml`). The statefulset is headless and exposes the RPC and metrics ports.

Apply the manifests (after building and pushing your image to a registry referenced in the StatefulSet):

```bash
kubectl apply -f k8s/spiron-service.yaml
kubectl apply -f k8s/spiron-statefulset.yaml
```

Important notes:
- The StatefulSet sets the pod name as `spiron_NODE_ID` (so each replica has a unique id available via the env var `spiron_NODE_ID`).
- By default the manifests assume the service uses the DNS discovery token pattern; the discovery provider can be selected at startup via `spiron.discovery.provider` property or CLI override.
 - By default the manifests assume the service uses the DNS discovery token pattern; the discovery provider can be selected at startup via the `spiron.discovery.provider` property, a CLI override, or the environment variable `SPIRON_DISCOVERY_PROVIDER`.


### Run Dependency Audit

```bash
./gradlew dependencyCheckAnalyze
```

---

## 🧭 Quick Usage Example (client API)

Below are small, accurate examples that match the public `SpironClient` API (the library's primary client).

Programmatic client (builder) with DNS peer expansion and propose/commit:

```java
import com.spiron.api.SpironClient;
import com.spiron.core.EddyState;

var client = new com.spiron.api.SpironClient.Builder()
       .peers(List.of("dns:service.example.com:8080")) // expands A records for service.example.com
       .workerThreads(4)
       .build();

// propose a new eddy (non-blocking helper available)
client.propose(new EddyState("proposal-1", new double[]{0.9, 0.1, 0.0}, 1.2));

// later, commit intent (blocks until the client has issued the commit RPC)
client.commit(new EddyState("proposal-1", new double[]{0.9, 0.1, 0.0}, 2.6));

client.close();
```

Start an embedded server from the client (programmatic):

```java
var embedded = client.startEmbeddedServer();
// ... run tests or local experimentation ...
embedded.stop();
```

Start via CLI (reads application.properties unless overridden):

```
# starts server reading application.properties unless overridden
java -cp build/libs/spiron.jar com.spiron.tools.SpironCli start-server --spiron.node.id=node-1 --spiron.port=8081 --spiron.cluster.peers=localhost:8081,localhost:8082
```

Metrics: Spiron now exposes richer RPC and engine metrics via Micrometer Prometheus endpoint (see `spiron.metrics.port`). Notable metric names:

- spiron_rpc_broadcast_total — number of broadcast RPCs sent/received
- spiron_rpc_commit_total — number of commit RPCs sent/received
- spiron_rpc_failures_total — RPC failures
- spiron_rpc_latency — RPC latency histogram (p50, p90, p99 percentiles)
- spiron_merges_total — number of merges performed by EddyEngine
- spiron_energy_levels — distribution of eddy energy values
- spiron_commit_latency — latency to commit a dominant eddy

These metrics are compatible with typical Prometheus scraping and Grafana dashboards. The built-in registry listens at `http://localhost:<metrics.port>/metrics` by default.

## 📖 Consensus Story — a concrete example

Imagine a small cluster of three Spirons (nodes A, B, C) coordinating to agree on a proposed plan represented as a 3-dimensional vector. We'll step through a typical run and the shape of client variables and network responses.

1) Startup: Each node runs with a unique `spiron.node.id` and a known peers list (or DNS token). Example CLI for node A:

```bash
java -cp build/libs/spiron.jar com.spiron.tools.SpironCli start-server \
       --spiron.node.id=node-A --spiron.port=8081 --spiron.cluster.peers=localhost:8081,localhost:8082,localhost:8083 \
       --spiron.damping.alpha=0.98 --spiron.siphon.factor=0.2 --spiron.angular.threshold=0.6 --spiron.commit.energy=2.5
```

2) Proposal: An LLM agent on node A proposes a vector (id="proposal-1", vec=[0.9,0.1,0.0], energy=1.2) by calling the client API `propose` (e.g. `SpironClient.propose(...)`).

3) Broadcast/Gossip: Node A's RpcClient sends the EddyState to peers; each receiving node ingests the state into its `EddyEngine` and emits a `spiron_rpc_broadcast_total` metric tagged with the remote peer.

4) Merge & Siphon: Each node compares incoming vectors to local eddies; if similarity > `angularThreshold` it siphons energy and merges. For example, if B sees similar vectors it may raise the energy to 2.0.

5) Dominance & Commit: When a node's dominant eddy energy crosses `commitEnergy` (2.5), it persists the commit (SpironRaftLog), signs the commit body (BLS) and broadcasts a `CommitEnvelope`.

6) Verification: Receiving peers verify the BLS signature or accept the `receiver-signs` fallback (test mode). Successful commits increment `spiron_rpc_commit_total` (per-peer tagged) and become durable.

Operational notes & metrics to watch
- `spiron_rpc_broadcast_total{peer="host:port"}` — broadcast counts per peer
- `spiron_rpc_commit_total{peer="host:port"}` — commit messages accepted per peer
- `spiron_rpc_latency` — RPC round-trip latency histogram
- `spiron_merges_total` & `spiron_energy_levels` — internal engine behavior (useful to understand convergence)

This story demonstrates how node-local proposals travel, influence energy, merge, and eventually reach a cluster commit — all while emitting per-peer and engine-level metrics suitable for observability.


---

## 🧠 Example Domains & Use Cases

| Domain                   | Example                                           | spiron’s Role                                          |
| ------------------------ | ------------------------------------------------- | ------------------------------------------------------- |
| **Multi-Agent AI**       | Multiple LLM agents propose next actions          | spiron merges semantic embeddings into a dominant plan |
| **Recommender Systems**  | Trending item detection from distributed behavior | Aggregates interest vectors to detect dominant trend    |
| **IoT & Edge Computing** | Sensor fusion, multi-sensor consensus             | Finds most representative environmental vector          |
| **Autonomous Swarms**    | Robots/drones share trajectories                  | Emergent shared direction without explicit leader       |
| **Federated ML / RL**    | Distributed gradient updates                      | Vector merge acts like semantic FedAvg                  |
| **Anomaly Detection**    | Multiple microservices reporting metrics          | High-energy dominant eddy = anomaly consensus           |

---

## 🔬 Comparison Table

| Feature                  | Raft      | Paxos        | Federated Avg      | Spiron                             |
| ------------------------ | --------- | ------------ | ------------------ | ----------------------------------- |
| Consensus Target         | Bytes     | Bytes        | Gradients          | Vectors / embeddings                |
| Leadership               | Elected   | Elected      | Central server     | Emergent dominant node              |
| Fault model              | Crash     | Crash        | Partial            | Probabilistic                       |
| Deterministic            | Yes       | Yes          | No                 | No (continuous)                     |
| Decentralized            | Partial   | Partial      | No                 | Yes                                 |
| Cryptographic Signatures | Optional  | No           | No                 | ✅ BLS aggregate signatures          |
| Use case                 | Databases | Coordination | Federated learning | AI consensus / semantic aggregation |

---

## Configuration & Properties

Spiron exposes operational configuration and observability options via properties and environment
variables. The table below summarizes the most important configuration keys, their corresponding
environment variable overrides (when available), defaults, and short descriptions. This is the
single authoritative place for configuration; other sections in this README have been cleaned up
to avoid duplication.

| Property | Env var | Default | Description |
| --- | --- | --- | --- |
| `spiron.node.id` | `SPIRON_NODE_ID` | (required) | Unique node identifier used for signing and logs |
| `spiron.port` | `SPIRON_PORT` | (required) | gRPC/RPC listen port for the node |
| `spiron.cluster.peers` | - | (required) | Comma-separated peer entries. Supports token syntax like `dns:hostname[:port]` to expand A records |
| `spiron.discovery.provider` | `SPIRON_DISCOVERY_PROVIDER` | `dns` | Selects a discovery provider registered with the discovery SPI (e.g. `dns`, `consul`, `k8s`) |
| `spiron.metrics.port` | `SPIRON_METRICS_PORT` | `9090` | Prometheus metrics HTTP port |
| `spiron.rpc.workerThreads` | - | `4` | Number of worker threads for RPC handling |
| `spiron.damping.alpha` | - | (required) | Damping factor for divergent eddies (e.g., `0.98`) |
| `spiron.siphon.factor` | - | (required) | Fraction of energy siphoned from similar eddies (e.g., `0.2`) |
| `spiron.angular.threshold` | - | (required) | Cosine similarity threshold to consider two vectors "similar" (e.g., `0.6`) |
| `spiron.commit.energy` | - | (required) | Energy threshold at which an eddy is considered committed/dominant |
| `spiron.data.dir` | - | (required) | Filesystem path for persistence (snapshots, logs) |
| `spiron.bls.seed` | - | `""` | Optional seed for local BLS signing key generation |

Notes:
- The `spiron.cluster.peers` entry is required and may include token forms such as `dns:service.default.svc.cluster.local:8080` which will be expanded by the discovery provider selected via `spiron.discovery.provider` (or `SPIRON_DISCOVERY_PROVIDER`).
- Environment variables take precedence where noted. CLI/override maps (used by the `SpironCli`) also take precedence over properties files where passed.

### Observability

- Spiron exposes Prometheus-compatible metrics at `http://<host>:<spiron.metrics.port>/metrics` by default. See `docs/metrics.md` for a detailed list and a sample Grafana dashboard at `docs/grafana/spiron_dashboard.json`.
- Important metric names you will commonly use in dashboards and alerts:
       - `spiron_rpc_broadcast_total` — number of broadcast RPCs sent/received (tagged by peer)
       - `spiron_rpc_commit_total` — number of commit notifications sent/received (tagged by peer)
       - `spiron_rpc_failures_total` — RPC failure counter
       - `spiron_rpc_latency` — RPC latency histogram (p50/p90/p99)
       - `spiron_merges_total` & `spiron_energy_levels` — internal engine metrics for convergence

### Discovery

- The default discovery provider is `dns`, which supports tokens in `spiron.cluster.peers` of the
       form `dns:hostname[:port]`. The discovery SPI allows swapping in other providers (for example:
       `consul`, a Kubernetes-based resolver, or SRV-based lookup). To select a provider at startup,
       set `spiron.discovery.provider` in `application.properties`, pass the key as a CLI override, or
       set the environment variable `SPIRON_DISCOVERY_PROVIDER`.

If you need a custom provider implemented (Consul or k8s API integration), please file an issue
or submit a PR; the discovery SPI is already pluggable and providers can be registered at runtime.

---

## 📚 Citations & Related Work

* “Self-Consistency Improves Chain-of-Thought” — Google, 2022
* “LLM Agents as Game Players” — Stanford, 2023
* “ConsensusGPT” — arXiv preprint, 2024
* “Decentralized Federated Averaging” — OpenAI / Meta, 2024
---

## ⚖️ License

```
Copyright (c) 2025 Density AI
Licensed under the Apache License, Version 2.0.
```
---
## Buy me a coffee

If you find Spiron useful and would like to support ongoing development, one simple way is to
buy the project maintainer a coffee. Contributions help with build infrastructure, documentation,
and future feature work.

[![Buy Me A Coffee](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/densityai)
---
