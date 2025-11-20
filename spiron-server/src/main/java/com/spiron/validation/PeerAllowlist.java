package com.spiron.validation;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates peer identities against an allowlist pattern.
 * Provides crash fault tolerance by rejecting unknown/malicious peers.
 */
public class PeerAllowlist {

  private static final Logger log = LoggerFactory.getLogger(PeerAllowlist.class);

  private final Pattern allowlistPattern;
  private final boolean enabled;

  /**
   * Creates a peer allowlist validator.
   * 
   * @param allowlistRegex regex pattern for allowed peer IDs (empty = allow all)
   */
  public PeerAllowlist(String allowlistRegex) {
    if (allowlistRegex == null || allowlistRegex.isBlank()) {
      this.enabled = false;
      this.allowlistPattern = null;
      log.info("Peer allowlist DISABLED - accepting all peers");
    } else {
      this.enabled = true;
      this.allowlistPattern = Pattern.compile(allowlistRegex);
      log.info("Peer allowlist ENABLED - pattern: {}", allowlistRegex);
    }
  }

  /**
   * Checks if a peer is allowed to send broadcasts.
   * 
   * @param peerId peer identifier (e.g., "host:port" or IP address)
   * @return true if peer is allowed, false if rejected
   */
  public boolean isAllowed(String peerId) {
    if (!enabled) {
      return true; // Allowlist disabled, accept all
    }
    
    if (peerId == null || peerId.isBlank()) {
      log.warn("Rejecting broadcast from null/empty peer ID");
      return false;
    }

    boolean matches = allowlistPattern.matcher(peerId).matches();
    if (!matches) {
      log.warn("Rejecting broadcast from non-allowlisted peer: {}", peerId);
    }
    return matches;
  }

  /**
   * Check if allowlist is enabled.
   */
  public boolean isEnabled() {
    return enabled;
  }
}
