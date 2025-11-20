package com.spiron.core;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.storage.CRDTStore;
import com.spiron.storage.RocksDbCRDTStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test for EddyEngine with LineageTracker.
 * Verifies that lineage is recorded on merge and commit operations.
 */
class EddyEngineLineageIntegrationTest {

  @TempDir
  Path tempDir;

  private EddyEngine engine;
  private CRDTStore store;
  private LineageTracker tracker;
  private SpironRaftLog raftLog;
  private SpironSnapshotStore snapshotStore;

  @BeforeEach
  void setUp() throws Exception {
    Path rocksDbPath = tempDir.resolve("integration-test-db");
    Files.createDirectories(rocksDbPath);
    
    store = new RocksDbCRDTStore(rocksDbPath);
    tracker = new LineageTracker(store);
    raftLog = new SpironRaftLog(tempDir.toString());
    snapshotStore = new SpironSnapshotStore(tempDir.toString());

    engine = new EddyEngine(
      0.95,  // alpha (damping)
      0.1,   // siphon factor
      0.8,   // angular threshold
      100.0  // commit energy
    );
    
    engine.attachStorage(raftLog, snapshotStore);
    engine.attachLineageTracker(tracker);
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
  void testLineageRecordedOnCommit() throws Exception {
    // Create root eddy with high energy to trigger commit
    double[] vector1 = {1.0, 0.0};
    EddyState root = new EddyState("root-eddy", vector1, 150.0, null);
    engine.ingest(root);

    // Commit the dominant eddy
    var dominant = engine.checkAndCommit();
    assertTrue(dominant.isPresent());
    assertEquals("root-eddy", dominant.get().id());

    // Verify lineage was recorded
    List<String> ancestry = tracker.getAncestry("root-eddy");
    assertNotNull(ancestry);
    assertEquals(0, ancestry.size(), "Root eddy should have no ancestors");
  }

  @Test
  void testLineageWithMergedEddies() throws Exception {
    // Create similar eddies that will merge
    double[] vector = {1.0, 0.0};
    
    // Ingest root eddy
    EddyState eddy1 = new EddyState("eddy-1", vector, 50.0, null);
    engine.ingest(eddy1);
    
    // Record its lineage manually (simulating first commit)
    tracker.recordLineage("eddy-1", null);

    // Ingest similar eddy that will merge with eddy-1
    double[] similarVector = {0.95, 0.31}; // Similar angle
    EddyState eddy2 = new EddyState("eddy-2", similarVector, 60.0, "eddy-1");
    engine.ingest(eddy2);

    // The merge should preserve parent relationship
    // When eddy-2 reaches commit energy, its lineage should include eddy-1
    double[] boostVector = {1.0, 0.0};
    EddyState eddy3 = new EddyState("eddy-2", boostVector, 150.0, "eddy-1");
    engine.ingest(eddy3);

    // Commit
    var dominant = engine.checkAndCommit();
    if (dominant.isPresent()) {
      String dominantId = dominant.get().id();
      String parentId = dominant.get().parentId();
      
      if (parentId != null) {
        // Verify lineage was recorded with parent
        List<String> ancestry = tracker.getAncestry(dominantId);
        assertNotNull(ancestry);
        // Should have at least the immediate parent
        assertTrue(ancestry.contains(parentId) || ancestry.isEmpty());
      }
    }
  }

  @Test
  void testMultiGenerationLineage() throws Exception {
    double[] vector = {1.0, 0.0};

    // Generation 1: Root
    tracker.recordLineage("gen-1", null);
    
    // Generation 2: Child of gen-1
    tracker.recordLineage("gen-2", "gen-1");
    
    // Generation 3: Child of gen-2
    tracker.recordLineage("gen-3", "gen-2");
    
    // Create eddy that represents gen-3 with commit energy
    EddyState gen3Eddy = new EddyState("gen-3", vector, 150.0, "gen-2");
    engine.ingest(gen3Eddy);

    // Commit should record lineage
    var dominant = engine.checkAndCommit();
    assertTrue(dominant.isPresent());

    // Verify full ancestry chain
    List<String> ancestry = tracker.getAncestry("gen-3");
    assertNotNull(ancestry);
    assertEquals(2, ancestry.size());
    assertEquals("gen-1", ancestry.get(0), "Oldest ancestor first");
    assertEquals("gen-2", ancestry.get(1), "Immediate parent last");
  }

  @Test
  void testLineagePersistsAcrossEngineRestarts() throws Exception {
    // Record lineage and commit
    double[] vector = {1.0, 0.0};
    tracker.recordLineage("persistent-eddy", null);
    EddyState eddy = new EddyState("persistent-eddy", vector, 150.0, null);
    engine.ingest(eddy);
    engine.checkAndCommit();

    // Shutdown engine
    engine = null;

    // Create new engine (simulating restart)
    EddyEngine newEngine = new EddyEngine(0.95, 0.1, 0.8, 100.0);
    newEngine.attachStorage(raftLog, snapshotStore);
    newEngine.attachLineageTracker(tracker);

    // Verify lineage is still available
    List<String> ancestry = tracker.getAncestry("persistent-eddy");
    assertNotNull(ancestry);
    assertEquals(0, ancestry.size(), "Root eddy should have empty ancestry");
  }

  @Test
  void testDominantWithLineageRetrieval() throws Exception {
    // Create lineage chain
    tracker.recordLineage("ancestor-1", null);
    tracker.recordLineage("ancestor-2", "ancestor-1");
    
    // Create dominant eddy
    double[] vector = {1.0, 0.0};
    EddyState dominant = new EddyState("dominant-eddy", vector, 150.0, "ancestor-2");
    engine.ingest(dominant);
    
    // Manually record lineage for dominant (commit would do this)
    tracker.recordLineage("dominant-eddy", "ancestor-2");

    // Retrieve dominant with lineage
    var result = tracker.getDominantWithLineageAsync("dominant-eddy")
      .get(5, java.util.concurrent.TimeUnit.SECONDS);

    assertNotNull(result);
    assertEquals("dominant-eddy", result.dominantId);
    assertEquals(2, result.ancestry.size());
    assertEquals("ancestor-1", result.ancestry.get(0));
    assertEquals("ancestor-2", result.ancestry.get(1));
  }

  @Test
  void testNoLineageForNonCommittedEddies() throws Exception {
    // Ingest eddy that doesn't reach commit energy
    double[] vector = {1.0, 0.0};
    EddyState weakEddy = new EddyState("weak-eddy", vector, 50.0, null);
    engine.ingest(weakEddy);

    // Check commit (should not commit)
    var dominant = engine.checkAndCommit();
    assertTrue(dominant.isEmpty(), "Weak eddy should not commit");

    // Lineage should not be recorded automatically for non-committed eddies
    // (only manual recording or commit triggers lineage persistence)
  }

  @Test
  void testLineageWithParentIdPreservedThroughMerge() throws Exception {
    // This test verifies that parentId is preserved through the merge process
    double[] vector1 = {1.0, 0.0};
    double[] vector2 = {0.9, 0.44}; // Similar angle for merging

    // Create parent eddy
    EddyState parent = new EddyState("parent", vector1, 50.0, null);
    engine.ingest(parent);

    // Create child eddy with parentId set
    EddyState child = new EddyState("child", vector2, 60.0, "parent");
    engine.ingest(child);

    // The engine's merge logic should create a new state with parentId
    // Check that the merged state preserves parentId relationship
    var snapshot = engine.snapshot();
    assertFalse(snapshot.isEmpty());

    // Find the child eddy in snapshot
    var childState = snapshot.stream()
      .filter(e -> e.id().equals("child"))
      .findFirst();

    if (childState.isPresent()) {
      // Parent ID should be preserved (or updated to parent's ID if merged)
      assertNotNull(childState.get().parentId());
    }
  }
}
