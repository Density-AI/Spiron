package com.spiron.api;

import com.spiron.core.*;
import com.spiron.metrics.EnergyMetrics;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Administrative operations for Spiron consensus engine.
 * 
 * <p>Provides operational monitoring and diagnostics capabilities including:
 * <ul>
 *   <li>State snapshots - capture current eddy states</li>
 *   <li>Metrics summaries - runtime performance data</li>
 * </ul>
 * 
 * <p><b>Usage Patterns:</b></p>
 * 
 * <p><b>1. Via Dependency Injection:</b></p>
 * <pre>{@code
 * SpironComponent component = DaggerSpironComponent.builder()
 *   .config(config)
 *   .build();
 * SpironAdmin admin = new SpironAdmin(
 *   component.engine(),
 *   component.energyMetrics()
 * );
 * List<EddyState> snapshot = admin.snapshot();
 * }</pre>
 * 
 * <p><b>2. Via CLI (Recommended for Operations):</b></p>
 * <pre>{@code
 * // Example CLI commands (to be implemented in SpironCli)
 * $ spiron snapshot --output snapshot.json
 * $ spiron metrics-summary
 * $ spiron admin status
 * }</pre>
 * 
 * <p><b>3. Via HTTP Endpoint (Future):</b></p>
 * <pre>{@code
 * GET /admin/snapshot
 * GET /admin/metrics/summary
 * }</pre>
 * 
 * <p><b>Current Status:</b> Class exists but not yet integrated into CLI.
 * Integration is planned for operational tooling.</p>
 * 
 * @see com.spiron.core.EddyEngine
 * @see com.spiron.metrics.EnergyMetrics
 */
public class SpironAdmin {

  private final EddyEngine engine;
  private final EnergyMetrics metrics;
  private final LineageTracker lineageTracker;

  public SpironAdmin(EddyEngine engine, EnergyMetrics metrics, LineageTracker lineageTracker) {
    this.engine = engine;
    this.metrics = metrics;
    this.lineageTracker = lineageTracker;
  }

  public List<EddyState> snapshot() {
    return engine.snapshot();
  }

  public String metricsSummary() {
    return """
    spiron Metrics:
      Merges/sec : %s
      Energy hist : %s
      Commit latency (p95) : %s ms
    """.formatted(
        metrics.toString(),
        "available via /metrics",
        "check Prometheus"
      );
  }
  
  /**
   * Get the current dominant eddy with its full ancestry lineage.
   * Returns null if no dominant eddy exists.
   * 
   * <p>Example usage:</p>
   * <pre>{@code
   * var result = admin.getDominantWithLineage();
   * if (result != null) {
   *   System.out.println("Dominant: " + result.dominantId);
   *   System.out.println("Ancestry: " + result.ancestry);
   * }
   * }</pre>
   */
  public LineageTracker.DominantWithLineage getDominantWithLineage() {
    var dominant = engine.dominant();
    if (dominant.isEmpty()) {
      return null;
    }
    
    String dominantId = dominant.get().id();
    try {
      return lineageTracker.getDominantWithLineageAsync(dominantId)
        .get(); // Block for sync API
    } catch (Exception e) {
      return null;
    }
  }
  
  /**
   * Get ancestry for a specific eddy asynchronously.
   */
  public CompletableFuture<List<String>> getAncestryAsync(String eddyId) {
    return lineageTracker.getAncestryAsync(eddyId);
  }
}
