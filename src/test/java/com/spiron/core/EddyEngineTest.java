package com.spiron.core;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.network.RpcClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EddyEngineTest {

  private EddyEngine engine;
  private FakeLog log;
  private FakeSnapshots snaps;
  private FakeRpc rpc;

  private final double alpha = 0.5;
  private final double siphonFactor = 0.2;
  private final double angularThreshold = 0.5;
  private final double commitEnergy = 3.0;

  private Path tempRoot;

  @BeforeEach
  void setUp() {
    tempRoot = mkTempDir("spiron-test-");

    engine = new EddyEngine(
      alpha,
      siphonFactor,
      angularThreshold,
      commitEnergy
    );
    try {
      log = new FakeLog(tempRoot.resolve("logroot").toString());
      snaps = new FakeSnapshots(tempRoot.resolve("snaproot").toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    rpc = new FakeRpc();

    engine.attachStorage(log, snaps);
    engine.attachNetwork(rpc);
  }

  @Test
  void ingest_lowSimilarity_appliesDampingOnExisting() {
    EddyState a = new EddyState("E", new double[] { 1.0, 0.0 }, 10.0);
    EddyState b = new EddyState("E", new double[] { 0.0, 1.0 }, 5.0);

    engine.ingest(a);
    engine.ingest(b);

    Optional<EddyState> dom = engine.dominant();
    assertTrue(dom.isPresent());
    assertEquals("E", dom.get().id());
    assertEquals(10.0 * alpha, dom.get().energy(), 1e-9);
    assertArrayEquals(a.vector(), dom.get().vector(), 1e-12);
  }

  @Test
  void dominant_present_only_when_energy_meets_commitThreshold() {
    engine.ingest(
      new EddyState("L", new double[] { 1, 0 }, commitEnergy - 0.01)
    );
    assertTrue(engine.dominant().isEmpty());

    engine.ingest(new EddyState("H", new double[] { 1, 0 }, commitEnergy));
    var dom = engine.dominant();
    assertTrue(dom.isPresent());
    assertEquals("H", dom.get().id());
  }

  @Test
  void checkAndCommit_callsRpcCommit_and_persists() {
    EddyState s = new EddyState(
      "C1",
      new double[] { 0.9, 0.3 },
      commitEnergy + 1.0
    );
    engine.ingest(s);

    var committed = engine.checkAndCommit();

    assertTrue(committed.isPresent());
    assertEquals("C1", committed.get().id());

    assertEquals(1, rpc.commitCalls);
    assertNotNull(rpc.lastCommitted);
    assertEquals("C1", rpc.lastCommitted.id());

    assertNotNull(log.lastLine);
    assertTrue(log.lastLine.startsWith("commit:C1,"));

    assertNotNull(snaps.lastSaved);
    assertEquals("C1", snaps.lastSaved.id());
  }

  @Test
  void persistState_updatesInMemory_so_dominant_can_become_present_via_rpc() {
    EddyState committed = new EddyState(
      "RPC",
      new double[] { 0.5, 0.5 },
      commitEnergy + 0.5
    );
    engine.persistState(committed);

    var dom = engine.dominant();
    assertTrue(dom.isPresent());
    assertEquals("RPC", dom.get().id());
    assertEquals(commitEnergy + 0.5, dom.get().energy(), 1e-9);
  }

  @Test
  void propagate_callsBroadcast_whenNetworkAttached() {
    EddyState s = new EddyState("B1", new double[] { 0.1, 0.2 }, 1.0);
    engine.propagate(s);
    assertEquals(1, rpc.broadcastCalls);
    assertNotNull(rpc.lastBroadcast);
    assertEquals("B1", rpc.lastBroadcast.id());
  }

  @Test
  void snapshot_returns_copy_and_is_immutable() {
    engine.ingest(new EddyState("S1", new double[] { 1, 0 }, 1.0));
    engine.ingest(new EddyState("S2", new double[] { 0, 1 }, 2.0));

    var snap = engine.snapshot();
    assertEquals(2, snap.size());
    assertThrows(UnsupportedOperationException.class, () ->
      snap.add(new EddyState("S3", new double[] { 0.3, 0.7 }, 3.0))
    );
  }

  // ---------- fakes that honor real constructors ----------

  static final class FakeLog extends SpironRaftLog {

    String lastLine;

    FakeLog(String root) throws IOException {
      super(root);
    }

    @Override
    public void append(String line) {
      lastLine = line;
    }
  }

  static final class FakeSnapshots extends SpironSnapshotStore {

    EddyState lastSaved;

    FakeSnapshots(String root) throws IOException {
      super(root);
    }

    @Override
    public void save(EddyState state) {
      lastSaved = state;
    }
  }

  static final class FakeRpc extends RpcClient {

    int broadcastCalls = 0;
    int commitCalls = 0;
    EddyState lastBroadcast;
    EddyState lastCommitted;

    FakeRpc() {
      super(List.of());
    }

    @Override
    public void broadcast(EddyState state) {
      broadcastCalls++;
      lastBroadcast = state;
    }

    @Override
    public void commit(EddyState state) {
      commitCalls++;
      lastCommitted = state;
    }
  }

  private static Path mkTempDir(String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
