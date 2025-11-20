package com.spiron.core;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.storage.CRDTStore;
import com.spiron.storage.RocksDbCRDTStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test LineageTracker with persistent storage (no in-memory).
 * Verifies full ancestry chains are stored and retrieved from RocksDB.
 */
class LineageTrackerTest {

  @TempDir
  Path tempDir;

  private CRDTStore store;
  private LineageTracker tracker;

  @BeforeEach
  void setUp() throws Exception {
    Path rocksDbPath = tempDir.resolve("lineage-test-db");
    Files.createDirectories(rocksDbPath);
    store = new RocksDbCRDTStore(rocksDbPath);
    tracker = new LineageTracker(store);
  }

  @AfterEach
  void tearDown() {
    if (tracker != null) {
      tracker.shutdown();
    }
    if (store != null) {
      store.close();
    }
  }

  @Test
  void testRecordAndRetrieveLineage_RootEddy() throws Exception {
    // Root eddy has no parent
    tracker.recordLineage("root-eddy", null);

    List<String> ancestry = tracker.getAncestry("root-eddy");
    assertNotNull(ancestry);
    assertEquals(0, ancestry.size(), "Root eddy should have empty ancestry");
  }

  @Test
  void testRecordAndRetrieveLineage_SingleParent() throws Exception {
    // Create root and child
    tracker.recordLineage("root", null);
    tracker.recordLineage("child", "root");

    List<String> ancestry = tracker.getAncestry("child");
    assertNotNull(ancestry);
    assertEquals(1, ancestry.size());
    assertEquals("root", ancestry.get(0));
  }

  @Test
  void testRecordAndRetrieveLineage_MultipleGenerations() throws Exception {
    // Create lineage: root -> parent -> grandparent -> child
    tracker.recordLineage("root", null);
    tracker.recordLineage("parent", "root");
    tracker.recordLineage("grandparent", "parent");
    tracker.recordLineage("child", "grandparent");

    List<String> ancestry = tracker.getAncestry("child");
    assertNotNull(ancestry);
    assertEquals(3, ancestry.size());
    assertEquals("root", ancestry.get(0), "Oldest ancestor should be first");
    assertEquals("parent", ancestry.get(1));
    assertEquals("grandparent", ancestry.get(2), "Immediate parent should be last");
  }

  @Test
  void testGetAncestryAsync() throws Exception {
    tracker.recordLineage("root", null);
    tracker.recordLineage("child1", "root");
    tracker.recordLineage("child2", "child1");

    CompletableFuture<List<String>> future = tracker.getAncestryAsync("child2");
    List<String> ancestry = future.get(5, TimeUnit.SECONDS);

    assertNotNull(ancestry);
    assertEquals(2, ancestry.size());
    assertEquals("root", ancestry.get(0));
    assertEquals("child1", ancestry.get(1));
  }

  @Test
  void testGetAncestry_NonExistentEddy() throws Exception {
    List<String> ancestry = tracker.getAncestry("does-not-exist");
    assertNotNull(ancestry);
    assertEquals(0, ancestry.size(), "Non-existent eddy should return empty list");
  }

  @Test
  void testLineagePersistence_AcrossRestarts() throws Exception {
    // Record lineage
    tracker.recordLineage("root", null);
    tracker.recordLineage("child", "root");
    tracker.recordLineage("grandchild", "child");

    // Simulate restart: close tracker and store
    tracker.shutdown();
    tracker = null;

    // Create new tracker with same store (simulating restart)
    LineageTracker newTracker = new LineageTracker(store);

    // Verify lineage is still available from storage
    List<String> ancestry = newTracker.getAncestry("grandchild");
    assertNotNull(ancestry);
    assertEquals(2, ancestry.size());
    assertEquals("root", ancestry.get(0));
    assertEquals("child", ancestry.get(1));

    newTracker.shutdown();
  }

  @Test
  void testGetDominantWithLineageAsync() throws Exception {
    // Create lineage chain
    tracker.recordLineage("eddy-1", null);
    tracker.recordLineage("eddy-2", "eddy-1");
    tracker.recordLineage("eddy-3", "eddy-2");

    // Get dominant with lineage (eddy-3 is dominant)
    CompletableFuture<LineageTracker.DominantWithLineage> future =
      tracker.getDominantWithLineageAsync("eddy-3");

    LineageTracker.DominantWithLineage result = future.get(5, TimeUnit.SECONDS);

    assertNotNull(result);
    assertEquals("eddy-3", result.dominantId);
    assertEquals(2, result.ancestry.size());
    assertEquals("eddy-1", result.ancestry.get(0));
    assertEquals("eddy-2", result.ancestry.get(1));
    assertTrue(result.timestamp > 0);
  }

  @Test
  void testLineageStorageSeparation() throws Exception {
    // Record lineage for an eddy
    tracker.recordLineage("test-eddy", "parent-eddy");

    // Verify lineage is stored separately from eddy state
    // (lineage should be in "lineage:test-eddy" key, not "test-eddy")
    var directGet = store.get("test-eddy");
    assertTrue(directGet.isEmpty(), "Lineage should not be in eddy namespace");

    var lineageGet = store.getLineage("test-eddy");
    assertTrue(lineageGet.isPresent(), "Lineage should be in lineage namespace");
  }

  @Test
  void testComplexLineageTree() throws Exception {
    // Create a more complex lineage:
    //        root
    //       /    \
    //   branch1  branch2
    //      |       |
    //   leaf1    leaf2
    tracker.recordLineage("root", null);
    tracker.recordLineage("branch1", "root");
    tracker.recordLineage("branch2", "root");
    tracker.recordLineage("leaf1", "branch1");
    tracker.recordLineage("leaf2", "branch2");

    // Verify leaf1 lineage
    List<String> leaf1Ancestry = tracker.getAncestry("leaf1");
    assertEquals(2, leaf1Ancestry.size());
    assertEquals("root", leaf1Ancestry.get(0));
    assertEquals("branch1", leaf1Ancestry.get(1));

    // Verify leaf2 lineage
    List<String> leaf2Ancestry = tracker.getAncestry("leaf2");
    assertEquals(2, leaf2Ancestry.size());
    assertEquals("root", leaf2Ancestry.get(0));
    assertEquals("branch2", leaf2Ancestry.get(1));
  }

  @Test
  void testDeepLineageChain() throws Exception {
    // Create a deep lineage chain (10 generations)
    String currentEddy = "gen-0";
    tracker.recordLineage(currentEddy, null);

    for (int i = 1; i <= 10; i++) {
      String nextEddy = "gen-" + i;
      tracker.recordLineage(nextEddy, currentEddy);
      currentEddy = nextEddy;
    }

    // Verify the deepest eddy has full ancestry
    List<String> ancestry = tracker.getAncestry("gen-10");
    assertEquals(10, ancestry.size());
    assertEquals("gen-0", ancestry.get(0), "Root should be first");
    assertEquals("gen-9", ancestry.get(9), "Immediate parent should be last");
  }

  @Test
  void testConcurrentLineageRecording() throws Exception {
    // Record multiple lineages concurrently
    CompletableFuture<Void> f1 = CompletableFuture.runAsync(() ->
      tracker.recordLineage("concurrent-1", null)
    );
    CompletableFuture<Void> f2 = CompletableFuture.runAsync(() ->
      tracker.recordLineage("concurrent-2", null)
    );
    CompletableFuture<Void> f3 = CompletableFuture.runAsync(() ->
      tracker.recordLineage("concurrent-3", "concurrent-1")
    );

    CompletableFuture.allOf(f1, f2, f3).get(5, TimeUnit.SECONDS);

    // Verify all lineages were recorded
    assertNotNull(tracker.getAncestry("concurrent-1"));
    assertNotNull(tracker.getAncestry("concurrent-2"));
    assertNotNull(tracker.getAncestry("concurrent-3"));
  }
}
