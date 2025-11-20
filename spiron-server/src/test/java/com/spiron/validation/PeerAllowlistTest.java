package com.spiron.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PeerAllowlistTest {

  @Test
  void testDisabledAllowlist() {
    var allowlist = new PeerAllowlist("");
    
    assertFalse(allowlist.isEnabled());
    assertTrue(allowlist.isAllowed("any-peer"));
    assertTrue(allowlist.isAllowed("192.168.1.1:8080"));
    assertTrue(allowlist.isAllowed("unknown"));
  }

  @Test
  void testLocalhostOnlyPattern() {
    var allowlist = new PeerAllowlist("^(localhost|127\\.0\\.0\\.1):.*$");
    
    assertTrue(allowlist.isEnabled());
    assertTrue(allowlist.isAllowed("localhost:8080"));
    assertTrue(allowlist.isAllowed("127.0.0.1:8081"));
    assertFalse(allowlist.isAllowed("192.168.1.1:8080"));
    assertFalse(allowlist.isAllowed("evil.com:8080"));
  }

  @Test
  void testLocalNetworkPattern() {
    var allowlist = new PeerAllowlist("^(localhost|127\\.0\\.0\\.1|192\\.168\\..*):.*$");
    
    assertTrue(allowlist.isAllowed("localhost:8080"));
    assertTrue(allowlist.isAllowed("127.0.0.1:8081"));
    assertTrue(allowlist.isAllowed("192.168.1.100:8082"));
    assertTrue(allowlist.isAllowed("192.168.255.255:9999"));
    assertFalse(allowlist.isAllowed("10.0.0.1:8080"));
    assertFalse(allowlist.isAllowed("evil.com:8080"));
  }

  @Test
  void testSpecificPeersPattern() {
    var allowlist = new PeerAllowlist("^(node-1|node-2|node-3):.*$");
    
    assertTrue(allowlist.isAllowed("node-1:8080"));
    assertTrue(allowlist.isAllowed("node-2:8081"));
    assertTrue(allowlist.isAllowed("node-3:8082"));
    assertFalse(allowlist.isAllowed("node-4:8083"));
    assertFalse(allowlist.isAllowed("attacker:8080"));
  }

  @Test
  void testNullPeerId() {
    var allowlist = new PeerAllowlist("^localhost:.*$");
    
    assertFalse(allowlist.isAllowed(null));
    assertFalse(allowlist.isAllowed(""));
    assertFalse(allowlist.isAllowed("   "));
  }

  @Test
  void testComplexPattern() {
    // Allow only specific ports on local network
    var allowlist = new PeerAllowlist("^192\\.168\\.1\\.[0-9]{1,3}:(8080|8081|8082)$");
    
    assertTrue(allowlist.isAllowed("192.168.1.100:8080"));
    assertTrue(allowlist.isAllowed("192.168.1.1:8081"));
    assertTrue(allowlist.isAllowed("192.168.1.255:8082"));
    assertFalse(allowlist.isAllowed("192.168.1.100:9999"));
    assertFalse(allowlist.isAllowed("192.168.2.100:8080"));
  }
}
