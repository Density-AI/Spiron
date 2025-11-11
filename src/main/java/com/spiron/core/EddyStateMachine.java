package com.spiron.core;

import com.spiron.api.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default internal state machine for spiron. Integrates with the EddyEngine.
 */
public class EddyStateMachine implements StateMachine {

  private static final Logger log = LoggerFactory.getLogger(
    EddyStateMachine.class
  );
  private final EddyEngine engine;

  public EddyStateMachine(EddyEngine engine) {
    this.engine = engine;
  }

  @Override
  public void apply(byte[] command) {
    try {
      var msg = new String(command);
      var parts = msg.split(",");
      var id = parts[0];
      var vector = new double[] {
        Double.parseDouble(parts[1]),
        Double.parseDouble(parts[2]),
      };
      var energy = Double.parseDouble(parts[3]);
      engine.ingest(new EddyState(id, vector, energy));
      log.info("Applied command -> {}", msg);
    } catch (Exception e) {
      log.error("Failed to apply command", e);
    }
  }

  @Override
  public byte[] query(byte[] key) {
    return engine.dominant().map(Object::toString).orElse("none").getBytes();
  }
}
