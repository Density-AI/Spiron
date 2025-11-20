package com.spiron.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.spiron.storage.CRDTStore;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LineageTracker manages ancestry chains for eddies.
 * 
 * <p><b>No in-memory storage:</b> All lineage is persisted to RocksDB/etcd
 * and retrieved asynchronously when needed. This ensures durability and
 * prevents memory bloat from large ancestry chains.</p>
 * 
 * <p>Lineage is stored as JSON arrays in the format:
 * <pre>{"ancestry": ["root_id", "parent_id", "grandparent_id"]}</pre>
 */
public class LineageTracker {
  
  private static final Logger log = LoggerFactory.getLogger(LineageTracker.class);
  
  private final CRDTStore store;
  private final Gson gson;
  private final ExecutorService executor;
  
  public LineageTracker(CRDTStore store) {
    this.store = store;
    this.gson = new Gson();
    this.executor = Executors.newFixedThreadPool(2); // Small pool for async ops
  }
  
  /**
   * Record lineage for an eddy synchronously (for commit path).
   * Builds the ancestry chain by retrieving parent's lineage from storage.
   * 
   * @param eddyId the eddy to record lineage for
   * @param parentId the immediate parent eddy id, or null if root
   */
  public void recordLineage(String eddyId, String parentId) {
    try {
      List<String> ancestry = new ArrayList<>();
      
      if (parentId != null) {
        // Retrieve parent's lineage from storage
        Optional<String> parentLineageJson = store.getLineage(parentId);
        if (parentLineageJson.isPresent()) {
          LineageData parentLineage = gson.fromJson(
            parentLineageJson.get(), 
            LineageData.class
          );
          if (parentLineage.ancestry != null) {
            ancestry.addAll(parentLineage.ancestry);
          }
        }
        // Add parent to ancestry
        ancestry.add(parentId);
      }
      
      // Store lineage for this eddy
      LineageData lineage = new LineageData();
      lineage.ancestry = ancestry;
      lineage.timestamp = System.currentTimeMillis();
      
      String json = gson.toJson(lineage);
      store.putLineage(eddyId, json);
      
      log.debug("Recorded lineage for eddy {} with {} ancestors", 
        eddyId, ancestry.size());
    } catch (Exception e) {
      log.error("Failed to record lineage for eddy {}", eddyId, e);
      // Don't throw - lineage is best-effort
    }
  }
  
  /**
   * Retrieve full ancestry chain for an eddy asynchronously.
   * Returns CompletableFuture to avoid blocking.
   * 
   * @param eddyId the eddy to retrieve lineage for
   * @return CompletableFuture with ordered list of ancestor IDs (oldest first)
   */
  public CompletableFuture<List<String>> getAncestryAsync(String eddyId) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        Optional<String> lineageJson = store.getLineage(eddyId);
        if (lineageJson.isPresent()) {
          LineageData lineage = gson.fromJson(
            lineageJson.get(), 
            LineageData.class
          );
          return lineage.ancestry != null 
            ? new ArrayList<>(lineage.ancestry) 
            : Collections.emptyList();
        }
        return Collections.emptyList();
      } catch (Exception e) {
        log.error("Failed to retrieve lineage for eddy {}", eddyId, e);
        return Collections.emptyList();
      }
    }, executor);
  }
  
  /**
   * Retrieve lineage synchronously (for commit/debug).
   * Use sparingly - prefer async version.
   */
  public List<String> getAncestry(String eddyId) {
    try {
      Optional<String> lineageJson = store.getLineage(eddyId);
      if (lineageJson.isPresent()) {
        LineageData lineage = gson.fromJson(
          lineageJson.get(), 
          LineageData.class
        );
        return lineage.ancestry != null 
          ? new ArrayList<>(lineage.ancestry) 
          : Collections.emptyList();
      }
      return Collections.emptyList();
    } catch (Exception e) {
      log.error("Failed to retrieve lineage for eddy {}", eddyId, e);
      return Collections.emptyList();
    }
  }
  
  /**
   * Get dominant eddy and its full lineage.
   * Returns both the dominant eddy ID and its ancestry chain.
   */
  public CompletableFuture<DominantWithLineage> getDominantWithLineageAsync(
    String dominantId
  ) {
    return getAncestryAsync(dominantId).thenApply(ancestry -> {
      DominantWithLineage result = new DominantWithLineage();
      result.dominantId = dominantId;
      result.ancestry = ancestry;
      result.timestamp = System.currentTimeMillis();
      return result;
    });
  }
  
  public void shutdown() {
    executor.shutdownNow();
  }
  
  /**
   * JSON storage format for lineage data.
   */
  private static class LineageData {
    List<String> ancestry;
    long timestamp;
  }
  
  /**
   * Result object combining dominant eddy with its lineage.
   */
  public static class DominantWithLineage {
    public String dominantId;
    public List<String> ancestry;
    public long timestamp;
    
    @Override
    public String toString() {
      return String.format("Dominant: %s, Ancestry: %s (depth=%d)", 
        dominantId, ancestry, ancestry.size());
    }
  }
}
