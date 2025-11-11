# Spiron Metrics

This document describes the core Prometheus metrics exposed by Spiron.

Metric names (Prometheus-friendly):

- `spiron_merges_total` - Total number of vortex merges observed by the EddyEngine.
- `spiron_energy_levels` - Distribution summary for observed eddy energies.
- `spiron_commit_latency` - Timer for latency between proposal ingestion and commit.

RPC metrics (in `RpcMetrics`):

- `spiron_rpc_broadcast_total{peer=...}` - Count of broadcast RPCs sent, tagged by peer.
- `spiron_rpc_commit_total{peer=...}` - Count of commit notify RPCs sent, tagged by peer.
- `spiron_rpc_failures_total{peer=...}` - Count of failed RPC attempts, tagged by peer.
- `spiron_rpc_latency{peer=...}` - Timer for RPC latency; percentiles published (50/90/99).
- `spiron_rpc_inflight` - Distribution of in-flight RPCs.

How to scrape:

Add the Spiron node as a Prometheus scrape target. By default the metrics endpoint is
available at `http://<node-host>:<spiron.metrics.port>/metrics`.

Suggested Grafana panels:

- RPC broadcast rate (per-peer) using `rate(spiron_rpc_broadcast_total[5m])`.
- RPC failure rate: `rate(spiron_rpc_failures_total[5m])`.
- Commit latency P50/P90 extracted from `histogram_quantile(0.9, sum(rate(spiron_rpc_latency_bucket[5m])) by (le, peer))`.
