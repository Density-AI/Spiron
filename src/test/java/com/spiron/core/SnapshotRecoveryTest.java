package com.spiron.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for snapshot-based recovery in EddyEngine.
 */
class SnapshotRecoveryTest {

  @Test
  void testRecoveryFromSnapshot(@TempDir java.nio.file.Path tmpDir)
    throws Exception {
    // Create engine and storage
    var engine1 = new EddyEngine(0.95, 0.2, 0.7, 2.5);
    var log = new SpironRaftLog(tmpDir.toString());
    var snapshots = new SpironSnapshotStore(tmpDir.toString());

    // Attach storage (should attempt recovery from empty snapshot)
    engine1.attachStorage(log, snapshots);

    // Create and persist a dominant eddy
    double[] vec = { 0.9, 0.1, 0.0, 0.0 };
    var eddy = new EddyState("test-eddy-1", vec, 3.0, null);
    engine1.persistState(eddy);

    // Verify snapshot was saved
    var loaded1 = snapshots.load();
    assertTrue(loaded1.isPresent(), "Snapshot should be saved");
    assertEquals("test-eddy-1", loaded1.get().id());

    // Create NEW engine and attach same storage (should recover)
    var engine2 = new EddyEngine(0.95, 0.2, 0.7, 2.5);
    engine2.attachStorage(log, snapshots);

    // Verify recovery happened
    var recovered = engine2.snapshot();
    assertEquals(1, recovered.size(), "Should recover 1 eddy from snapshot");
    assertEquals("test-eddy-1", recovered.get(0).id());
    assertEquals(3.0, recovered.get(0).energy(), 0.001);
  }

  @Test
  void testNoRecoveryWhenNoSnapshot(@TempDir java.nio.file.Path tmpDir)
    throws Exception {
    var engine = new EddyEngine(0.95, 0.2, 0.7, 2.5);
    var log = new SpironRaftLog(tmpDir.toString());
    var snapshots = new SpironSnapshotStore(tmpDir.toString());

    // Attach storage with no existing snapshot
    engine.attachStorage(log, snapshots);

    // Should have no eddies
    var recovered = engine.snapshot();
    assertEquals(0, recovered.size(), "Should have no eddies without snapshot");
  }

  @Test
  void testLogReplay(@TempDir java.nio.file.Path tmpDir) throws Exception {
    var engine = new EddyEngine(0.95, 0.2, 0.7, 2.5);
    var log = new SpironRaftLog(tmpDir.toString());
    var snapshots = new SpironSnapshotStore(tmpDir.toString());

    engine.attachStorage(log, snapshots);

    // Manually write log entries (simulating commits)
    for (int i = 0; i < 5; i++) {
      log.append("commit:eddy-" + i + ",3." + i);
    }

    // Verify log has entries
    var logEntries = log.readAll();
    assertEquals(
      5,
      logEntries.size(),
      "Log should have 5 entries after writing"
    );

    // Replay log
    int count = engine.replayLog();
    assertEquals(5, count, "Should replay 5 commit entries");
  }
}

