package com.spiron.network;

import com.spiron.crdt.CRDTMergeEngine;
import com.spiron.crdt.FinallityDetector;
import com.spiron.proto.EddyGossipGrpc;
import com.spiron.proto.EddyProto;
import com.spiron.proto.EddyProto.*;
import com.spiron.serialization.CRDTJsonCodec;
import com.spiron.storage.CRDTStore;
import io.grpc.stub.StreamObserver;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EddyGossipService: gRPC service for CRDT synchronization (Sync RPC).
 *
 * Handles incoming SyncRequest from peers:
 * 1. Merges remote Eddy states with local state using CRDT merge
 * 2. Persists merged state to CRDTStore
 * 3. Returns local state in SyncResponse for peer to merge
 */
public class EddyGossipService extends EddyGossipGrpc.EddyGossipImplBase {

  private static final Logger log = LoggerFactory.getLogger(
    EddyGossipService.class
  );

  private final CRDTStore crdtStore;
  private final CRDTJsonCodec codec;
  private final FinallityDetector finalityDetector;
  private final long finalityThreshold;

  public EddyGossipService(CRDTStore crdtStore, CRDTJsonCodec codec, long finalityThreshold) {
    this.crdtStore = crdtStore;
    this.codec = codec;
    this.finalityDetector = new FinallityDetector();
    this.finalityThreshold = finalityThreshold;
  }

  @Override
  public void sync(SyncRequest request, StreamObserver<SyncResponse> observer) {
    try {
      String senderId = request.getSenderId();
      Map<String, EddyProto.CRDTEddy> remoteEddies = request.getEddiesMap();

      log.debug(
        "Received sync from {}: {} eddies",
        senderId,
        remoteEddies.size()
      );

      // Merge remote eddies with local state
      for (Map.Entry<
        String,
        EddyProto.CRDTEddy
      > entry : remoteEddies.entrySet()) {
        String eddyId = entry.getKey();
        EddyProto.CRDTEddy remoteEddy = entry.getValue();

        try {
          // Get local state (if exists)
          Optional<String> localJsonOpt = crdtStore.get(eddyId);
          EddyProto.CRDTEddy merged = remoteEddy;

          if (localJsonOpt.isPresent()) {
            EddyProto.CRDTEddy localEddy = codec.deserializeEddy(
              localJsonOpt.get()
            );
            if (localEddy != null) {
              merged = CRDTMergeEngine.merge(localEddy, remoteEddy);
            }
          }

          // Persist merged state
          String mergedJson = codec.serializeEddy(merged);
          crdtStore.put(eddyId, mergedJson);

          // Check for finality
          Optional<Long> finality = finalityDetector.checkFinality(eddyId, merged, finalityThreshold);
          if (finality.isPresent()) {
            log.info("Eddy {} reached finality after sync: cumulative approvals={}", 
              eddyId, finality.get());
          }

          log.debug("Synced eddy {}: merged with remote state", eddyId);
        } catch (Exception e) {
          log.warn("Failed to merge eddy {}", eddyId, e);
        }
      }

      // Build response: send local state back to peer
      SyncResponse.Builder responseBuilder = SyncResponse.newBuilder();

      try {
        Map<String, String> allLocal = crdtStore.getAll();
        for (Map.Entry<String, String> entry : allLocal.entrySet()) {
          String eddyId = entry.getKey();
          String jsonState = entry.getValue();
          EddyProto.CRDTEddy eddy = codec.deserializeEddy(jsonState);
          if (eddy != null) {
            responseBuilder.putEddies(eddyId, eddy);
          }
        }
      } catch (Exception e) {
        log.warn("Failed to read local eddies for response", e);
      }

      observer.onNext(responseBuilder.build());
      observer.onCompleted();
    } catch (Exception e) {
      log.error("Sync RPC failed", e);
      observer.onError(e);
    }
  }

  /**
   * Serialize Eddy to JSON string.
   *
   * @param eddy the eddy to serialize
   * @return JSON string
   */
  private String serializeEddy(EddyProto.CRDTEddy eddy) {
    return codec.serializeEddy(eddy);
  }
}
