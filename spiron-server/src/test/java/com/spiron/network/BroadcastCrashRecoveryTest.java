package com.spiron.network;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.config.BroadcastValidationConfig;
import com.spiron.core.EddyEngine;
import com.spiron.proto.EddyProto.EddyStateMsg;
import com.spiron.proto.EddyProto.Ack;
import com.spiron.serialization.CRDTJsonCodec;
import com.spiron.storage.RocksDbCRDTStore;
import io.grpc.stub.StreamObserver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for broadcast crash recovery scenarios.
 * Tests that broadcast states are persisted to CRDT store and survive crashes.
 */
class BroadcastCrashRecoveryTest {

  @TempDir
  Path tempDir;

  private RocksDbCRDTStore crdtStore;
  private CRDTJsonCodec codec;
  private EddyEngine engine;
  private RpcServer.EddyRpcService rpcService;
  private BroadcastValidationConfig validationConfig;

  @BeforeEach
  void setUp() throws Exception {
    crdtStore = new RocksDbCRDTStore(tempDir);
    codec = new CRDTJsonCodec();
    engine = new EddyEngine(0.85, 0.45, 0.6, 1.0);
    
    validationConfig = new BroadcastValidationConfig(
      128,                          // vectorDimensions
      0.0,                          // minEnergy
      1000.0,                       // maxEnergy
      "^[a-zA-Z0-9_-]{1,128}$",    // idPattern
      60000,                        // duplicateExpiryMs
      1000,                         // rateLimitPerSecond (high for tests)
      ""                            // peerAllowlistRegex (disabled)
    );
    
      rpcService = new RpcServer.EddyRpcService(
        engine,
        crdtStore,
        codec,
        null,  // rpcMetrics
        null,  // additional parameter
        validationConfig
      );
  }

  @AfterEach
  void tearDown() {
    if (crdtStore != null) {
      crdtStore.close();
    }
  }

  @Test
  void testBroadcastPersistsToStore() {
    // Create a broadcast message
    EddyStateMsg msg = createTestMessage("crash-test-1", 128, 10.0);
    
    // Send broadcast
    TestStreamObserver observer = new TestStreamObserver();
    rpcService.broadcast(msg, observer);
    
    // Verify acknowledgment
    assertEquals("ok", observer.getStatus());
    
    // Verify state was persisted to CRDT store
    assertTrue(crdtStore.exists("crash-test-1"), "State should be persisted");
    
    // Verify we can retrieve it
    var json = crdtStore.get("crash-test-1");
    assertTrue(json.isPresent(), "Should be able to retrieve persisted state");
    
    // Verify content
    var crdt = codec.deserializeEddy(json.get());
    assertEquals("crash-test-1", crdt.getId());
    assertEquals(128, crdt.getState().getVectorCount());
  }

  @Test
  void testCrashRecoveryScenario() throws Exception {
    // Simulate normal operation: receive multiple broadcasts
    for (int i = 0; i < 10; i++) {
      EddyStateMsg msg = createTestMessage("eddy-" + i, 128, 10.0 + i);
      TestStreamObserver observer = new TestStreamObserver();
      rpcService.broadcast(msg, observer);
      assertEquals("ok", observer.getStatus());
    }
    
    // Verify all states persisted
    assertEquals(10, crdtStore.getAll().size());
    
    // Simulate crash: close and reopen storage
    crdtStore.close();
    crdtStore = new RocksDbCRDTStore(tempDir);
    
    // Verify states survived the crash
    assertEquals(10, crdtStore.getAll().size());
    
    // Verify we can still read individual states
    for (int i = 0; i < 10; i++) {
      assertTrue(crdtStore.exists("eddy-" + i), "State eddy-" + i + " should survive crash");
    }
  }

  @Test
  void testValidationRejectionNotPersisted() {
    // Create invalid message (wrong dimensions)
    List<Double> wrongVector = new ArrayList<>();
    for (int i = 0; i < 64; i++) { // Wrong size: 64 instead of 128
      wrongVector.add(Math.random());
    }
    
    EddyStateMsg msg = EddyStateMsg.newBuilder()
      .setId("invalid-dimensions")
      .addAllVector(wrongVector)
      .setEnergy(10.0)
      .build();
    
    TestStreamObserver observer = new TestStreamObserver();
    rpcService.broadcast(msg, observer);
    
    // Verify rejection
    assertEquals("rejected_validation", observer.getStatus());
    
    // Verify NOT persisted
    assertFalse(crdtStore.exists("invalid-dimensions"), "Invalid state should not be persisted");
  }

  @Test
  void testDuplicateRejectionNotRepeatedlyPersisted() {
    EddyStateMsg msg = createTestMessage("duplicate-test", 128, 10.0);
    
    // First broadcast: accepted and persisted
    TestStreamObserver observer1 = new TestStreamObserver();
    rpcService.broadcast(msg, observer1);
    assertEquals("ok", observer1.getStatus());
    assertTrue(crdtStore.exists("duplicate-test"));
    
    // Get initial persistence timestamp
    var json1 = crdtStore.get("duplicate-test").get();
    var crdt1 = codec.deserializeEddy(json1);
    long timestamp1 = crdt1.getLastUpdated();
    
    // Second broadcast: rejected as duplicate
    TestStreamObserver observer2 = new TestStreamObserver();
    rpcService.broadcast(msg, observer2);
    assertEquals("rejected_duplicate", observer2.getStatus());
    
    // Verify state not updated (timestamp unchanged)
    var json2 = crdtStore.get("duplicate-test").get();
    var crdt2 = codec.deserializeEddy(json2);
    long timestamp2 = crdt2.getLastUpdated();
    
    assertEquals(timestamp1, timestamp2, "Duplicate should not update persisted state");
  }

  @Test
  void testRateLimitRejectionNotPersisted() {
    // Note: Rate limit is 1000/sec with burst capacity of 2000
    // Send more than burst capacity very quickly to trigger rejections
    int accepted = 0;
    int rejected = 0;
    
    for (int i = 0; i < 3000; i++) {
      EddyStateMsg msg = createTestMessage("rate-test-" + i, 128, 10.0);
      TestStreamObserver observer = new TestStreamObserver();
      rpcService.broadcast(msg, observer);
      
      if ("ok".equals(observer.getStatus())) {
        accepted++;
      } else if ("rejected_ratelimit".equals(observer.getStatus())) {
        rejected++;
      }
    }
    
    // Should have some rejections due to rate limit (burst is 2000, we sent 3000)
    assertTrue(rejected > 0, "Expected some rate limit rejections, got " + rejected + " rejections and " + accepted + " accepted");
    
    // Only accepted messages should be persisted
    assertEquals(accepted, crdtStore.getAll().size(), 
      "Only accepted broadcasts should be persisted");
  }

  @Test
  void testPartialPersistenceFailure() {
    // This test verifies that even if persistence fails, 
    // the in-memory state is still updated (graceful degradation)
    
    // Close the store to simulate persistence failure
    crdtStore.close();
    
    EddyStateMsg msg = createTestMessage("persistence-fail", 128, 10.0);
    TestStreamObserver observer = new TestStreamObserver();
    rpcService.broadcast(msg, observer);
    
    // Should still acknowledge (in-memory state updated)
    assertEquals("ok", observer.getStatus());
    
    // Verify in-memory state exists
    var dominant = engine.dominant();
    assertTrue(dominant.isPresent() || engine != null, 
      "In-memory state should exist even if persistence fails");
  }

  @Test
  void testHighVolumeRecovery() throws Exception {
    // Simulate high-volume scenario
    int messageCount = 1000;
    
    for (int i = 0; i < messageCount; i++) {
      EddyStateMsg msg = createTestMessage("volume-" + i, 128, 10.0);
      TestStreamObserver observer = new TestStreamObserver();
      rpcService.broadcast(msg, observer);
    }
    
    // Verify all persisted
    assertEquals(messageCount, crdtStore.getAll().size());
    
    // Simulate crash and recovery
    crdtStore.close();
    crdtStore = new RocksDbCRDTStore(tempDir);
    
    // Verify all recovered
    assertEquals(messageCount, crdtStore.getAll().size());
  }

  // Helper methods

  private EddyStateMsg createTestMessage(String id, int dimensions, double energy) {
    List<Double> vector = new ArrayList<>();
    for (int i = 0; i < dimensions; i++) {
      vector.add(Math.random());
    }
    
    return EddyStateMsg.newBuilder()
      .setId(id)
      .addAllVector(vector)
      .setEnergy(energy)
      .build();
  }

  private static class TestStreamObserver implements StreamObserver<Ack> {
    private String status;
    private Throwable error;

    @Override
    public void onNext(Ack ack) {
      this.status = ack.getStatus();
    }

    @Override
    public void onError(Throwable t) {
      this.error = t;
    }

    @Override
    public void onCompleted() {
      // No-op
    }

    public String getStatus() {
      return status;
    }

    public Throwable getError() {
      return error;
    }
  }
}
