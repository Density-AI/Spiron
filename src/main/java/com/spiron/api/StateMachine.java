package com.spiron.api;

/**
 * Pluggable state machine API similar to Apache Ratis.
 * Implement to apply and query commands through consensus.
 */
public interface StateMachine {
  /** Apply a deterministic mutation command. */
  void apply(byte[] command);
  /** Query current state (read-only). */
  byte[] query(byte[] key);
}
