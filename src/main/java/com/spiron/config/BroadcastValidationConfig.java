package com.spiron.config;

/**
 * Configuration for broadcast message validation.
 * Loaded from application.properties.
 */
public record BroadcastValidationConfig(
  int vectorDimensions,
  double minEnergy,
  double maxEnergy,
  String idPattern,
  long duplicateExpiryMs,
  int rateLimitPerSecond,
  String peerAllowlistRegex
) {
  
  /**
   * Creates config from SpironConfig properties.
   */
  public static BroadcastValidationConfig fromSpironConfig(SpironConfig config) {
    return new BroadcastValidationConfig(
      config.vectorDimensions(),
      config.broadcastMinEnergy(),
      config.broadcastMaxEnergy(),
      config.broadcastIdPattern(),
      config.broadcastDuplicateExpiryMs(),
      config.broadcastRateLimitPerSecond(),
      config.broadcastPeerAllowlistRegex()
    );
  }
}
