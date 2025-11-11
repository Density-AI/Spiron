package com.spiron.di;

import com.spiron.config.SpironConfig;
import com.spiron.core.EddyEngine;
import com.spiron.core.SpironRaftLog;
import com.spiron.core.SpironSnapshotStore;
import com.spiron.metrics.EnergyMetrics;
import com.spiron.metrics.MetricsRegistry;
import com.spiron.network.RpcClient;
import com.spiron.network.RpcServer;
import com.spiron.security.BlsSigner;
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
    RpcClient client
  ) {
    var engine = new EddyEngine(
      cfg.dampingAlpha(),
      cfg.siphonFactor(),
      cfg.angularThreshold(),
      cfg.commitEnergy()
    );
    engine.attachStorage(log, store);
    engine.attachNetwork(client);
    return engine;
  }

  @Provides
  @Singleton
  RpcServer provideRpcServer(
    SpironConfig cfg,
    EddyEngine engine,
    com.spiron.metrics.RpcMetrics rpcMetrics
  ) {
    return new RpcServer(cfg.port(), engine, rpcMetrics);
  }

  @Provides
  @Singleton
  RpcClient provideRpcClient(
    SpironConfig cfg,
    BlsSigner signer,
    com.spiron.metrics.RpcMetrics rpcMetrics
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
  com.spiron.metrics.RpcMetrics provideRpcMetrics(MetricsRegistry metrics) {
    return new com.spiron.metrics.RpcMetrics(metrics.registry());
  }

  @Provides
  @Singleton
  EnergyMetrics provideEnergyMetrics(MetricsRegistry registry) {
    return new EnergyMetrics(registry.registry());
  }
}
