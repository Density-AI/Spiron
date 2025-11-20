package com.spiron;

import com.spiron.api.*;
import com.spiron.config.SpironConfig;
import com.spiron.core.EddyEngine;
import com.spiron.di.DaggerSpironComponent;
import com.spiron.di.SpironComponent;
import com.spiron.metrics.MetricsRegistry;
import com.spiron.network.RpcServer;
import com.spiron.security.BlsSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entrypoint for spiron node. (examples module) */
public class App {

  private static final Logger log = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) {
    final SpironConfig cfg = SpironConfig.load();
    byte[] seed = cfg.blsSeed() == null || cfg.blsSeed().isEmpty()
      ? cfg.nodeId().getBytes()
      : cfg.blsSeed().getBytes();
    BlsSigner signer = new BlsSigner(seed);
    final SpironComponent component = DaggerSpironComponent.builder()
      .config(cfg)
      .blsSigner(signer)
      .build();

    final EddyEngine engine = component.engine();
    final RpcServer rpcServer = component.rpcServer();
    component.metricsRegistry();
    component.energyMetrics();

    final SpironServer server = new SpironServer(rpcServer, engine);
    final Thread serverThread = new Thread(server, "spiron-server");
    serverThread.setDaemon(false);
    serverThread.start();

    log.info(
      "Spiron node started — metrics at :%s/metrics".formatted(
          cfg.metricsPort()
        )
    );

    // Graceful shutdown hook
    Runtime.getRuntime()
      .addShutdownHook(
        new Thread(
          () -> {
            try {
              server.shutdown();
              serverThread.join(2000);
            } catch (InterruptedException ignored) {
              Thread.currentThread().interrupt();
            }
          },
          "spiron-shutdown"
        )
      );
  }
}
