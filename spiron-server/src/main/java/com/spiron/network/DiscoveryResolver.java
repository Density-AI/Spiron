package com.spiron.network;

import java.net.InetAddress;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers peers via Kubernetes Headless Service DNS.
 * Example pattern: spiron-0.spiron.default.svc.cluster.local
 */
public class DiscoveryResolver {

  private static final Logger log = LoggerFactory.getLogger(
    DiscoveryResolver.class
  );

  public static List<String> resolvePeers(
    String service,
    int replicas,
    int port
  ) {
    List<String> peers = new ArrayList<>();
    for (int i = 0; i < replicas; i++) {
      String host = "spiron-%d.%s".formatted(i, service);
      try {
        InetAddress.getByName(host); // Validate DNS
        peers.add(host + ":" + port);
      } catch (Exception e) {
        log.warn("DNS resolution failed for {}", host);
      }
    }
    log.info("Resolved peers: {}", peers);
    return peers;
  }
}
