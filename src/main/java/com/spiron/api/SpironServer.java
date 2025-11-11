package com.spiron.api;

import com.spiron.core.*;
import com.spiron.network.RpcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * spiron server abstraction managing RPC + engine loop.
 */
public class SpironServer implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(SpironServer.class);
  private final RpcServer rpcServer;
  private final EddyEngine engine;
  private volatile boolean running = true;

  public SpironServer(RpcServer rpcServer, EddyEngine engine) {
    this.rpcServer = rpcServer;
    this.engine = engine;
  }

  @Override
  public void run() {
    try {
      rpcServer.start();
      log.info("Spiron server running...");
      while (running) {
        engine
          .checkAndCommit()
          .ifPresent(e ->
            log.info("Commit: {} energy={}", e.id(), e.energy())
          );
        Thread.sleep(300);
      }
    } catch (Exception e) {
      log.error("SpironServer error", e);
    }
  }

  public void shutdown() {
    running = false;
    log.info("Spiron server shutting down...");
  }
}
