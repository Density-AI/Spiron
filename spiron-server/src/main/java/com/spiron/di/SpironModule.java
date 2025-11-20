package com.spiron.di;

import com.spiron.config.BroadcastValidationConfig;
import com.spiron.config.SpironConfig;
import com.spiron.core.EddyEngine;
import com.spiron.core.LineageTracker;
import com.spiron.core.SpironRaftLog;
import com.spiron.core.SpironSnapshotStore;
import com.spiron.crdt.ApprovalCounter;
import com.spiron.crdt.CRDTMergeEngine;
import com.spiron.metrics.EnergyMetrics;
import com.spiron.metrics.MetricsRegistry;
import com.spiron.metrics.MetricsUpdater;
import com.spiron.metrics.RpcMetrics;
import com.spiron.metrics.StorageMetrics;
import com.spiron.metrics.ThroughputMetrics;
import com.spiron.network.RpcClient;
import com.spiron.network.RpcServer;
import com.spiron.security.BlsSigner;
import com.spiron.serialization.CRDTJsonCodec;
import com.spiron.storage.CRDTStore;
import com.spiron.storage.EtcdCRDTStore;
import com.spiron.storage.RocksDbCRDTStore;
import dagger.Module;
import dagger.Provides;
import java.io.IOException;
import javax.inject.Singleton;

/** Provides spiron dependency bindings. */
@Module
public class SpironModule {

  @Provides
  @Singleton
  EddyEngine provideEngine(
    SpironConfig cfg,
    SpironRaftLog log,
    SpironSnapshotStore store,
    RpcClient client,
    EnergyMetrics energyMetrics,
    ThroughputMetrics throughputMetrics,
    StorageMetrics storageMetrics,
    LineageTracker lineageTracker
  ) {
    var engine = new EddyEngine(
      cfg.dampingAlpha(),
      cfg.siphonFactor(),
      cfg.angularThreshold(),
      cfg.commitEnergy()
    );
    engine.attachStorage(log, store);
    engine.attachNetwork(client);
    engine.attachMetrics(energyMetrics);
    engine.attachThroughputMetrics(throughputMetrics);
    engine.attachStorageMetrics(storageMetrics);
    engine.attachLineageTracker(lineageTracker);
    return engine;
  }

  @Provides
  @Singleton
  BroadcastValidationConfig provideBroadcastValidationConfig(SpironConfig cfg) {
    return BroadcastValidationConfig.fromSpironConfig(cfg);
  }

  @Provides
  @Singleton
  RpcServer provideRpcServer(
    SpironConfig cfg,
    EddyEngine engine,
    CRDTStore crdtStore,
    CRDTJsonCodec codec,
    RpcMetrics rpcMetrics,
    MetricsUpdater metricsUpdater,
    StorageMetrics storageMetrics,
    BroadcastValidationConfig validationConfig
  ) {
    return new RpcServer(cfg.port(), engine, crdtStore, codec, rpcMetrics, metricsUpdater, storageMetrics, validationConfig, cfg.finalityThreshold());
  }

  @Provides
  @Singleton
  RpcClient provideRpcClient(
    SpironConfig cfg,
    BlsSigner signer,
    RpcMetrics rpcMetrics
  ) {
    return new RpcClient(
      cfg.peers(),
      signer,
      cfg.rpcWorkerThreads(),
      rpcMetrics
    );
  }

  @Provides
  @Singleton
  SpironRaftLog provideLog(SpironConfig cfg) {
    try {
      return new SpironRaftLog(cfg.dataDir());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create spironRaftLog", e);
    }
  }

  @Provides
  @Singleton
  SpironSnapshotStore provideSnapshots(SpironConfig cfg) {
    try {
      return new SpironSnapshotStore(cfg.dataDir());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create spironSnapshotStore", e);
    }
  }

  @Provides
  @Singleton
  MetricsRegistry provideMetricsRegistry(SpironConfig cfg) {
    var reg = new MetricsRegistry();
    reg.startHttp(cfg.metricsPort());
    return reg;
  }

  @Provides
  @Singleton
  RpcMetrics provideRpcMetrics(MetricsRegistry metrics) {
    return new RpcMetrics(metrics.registry());
  }

  @Provides
  @Singleton
  EnergyMetrics provideEnergyMetrics(MetricsRegistry registry) {
    EnergyMetrics energyMetrics = new EnergyMetrics(registry.registry());
    // Initialize CRDT class metrics
    CRDTMergeEngine.setMetrics(energyMetrics);
    ApprovalCounter.setMetrics(energyMetrics);
    return energyMetrics;
  }
  
  @Provides
  @Singleton
  StorageMetrics provideStorageMetrics(MetricsRegistry registry) {
    return new StorageMetrics(registry.registry());
  }
  
  @Provides
  @Singleton
  ThroughputMetrics provideThroughputMetrics(MetricsRegistry registry) {
    return new ThroughputMetrics(registry.registry());
  }
  
  @Provides
  @Singleton
  MetricsUpdater provideMetricsUpdater(
    StorageMetrics storageMetrics,
    ThroughputMetrics throughputMetrics,
    CRDTStore crdtStore
  ) {
    // Only create updater if we have RocksDB store
    if (crdtStore instanceof RocksDbCRDTStore) {
      return new MetricsUpdater(
        storageMetrics,
        throughputMetrics,
        (RocksDbCRDTStore) crdtStore
      );
    }
    return null;
  }

  @Provides
  @Singleton
  CRDTJsonCodec provideCRDTJsonCodec() {
    return new CRDTJsonCodec();
  }
  
  @Provides
  @Singleton
  LineageTracker provideLineageTracker(CRDTStore crdtStore) {
    return new LineageTracker(crdtStore);
  }

  @Provides
  @Singleton
  CRDTStore provideCRDTStore(SpironConfig cfg) {
    String storageMode = cfg.storageMode();
    try {
      if ("cluster".equals(storageMode)) {
        return new EtcdCRDTStore(cfg.etcdEndpoints());
      } else {
        // Default to solo mode (RocksDB)
        return new RocksDbCRDTStore(
          java.nio.file.Paths.get(cfg.dataDir(), "crdt")
        );
      }
    } catch (Exception e) {
      throw new RuntimeException(
        "Failed to initialize CRDTStore with mode: " + storageMode,
        e
      );
    }
  }
}
