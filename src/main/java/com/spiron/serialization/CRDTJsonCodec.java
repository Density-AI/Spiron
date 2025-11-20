package com.spiron.serialization;

import com.google.gson.*;
import com.google.protobuf.Message;
import com.spiron.proto.EddyProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON codec for proto message serialization/deserialization using Gson.
 *
 * Converts proto messages to/from JSON for persistence and gossip.
 * Supports CRDTEddy, CRDTVector, ApprovalCounter, and other EddyProto types.
 */
public class CRDTJsonCodec {

  private static final Logger log = LoggerFactory.getLogger(
    CRDTJsonCodec.class
  );

  private final Gson gson;

  public CRDTJsonCodec() {
    this.gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
  }

  /**
   * Serialize a CRDTEddy proto to JSON string.
   *
   * @param eddy the CRDT eddy to serialize
   * @return JSON string representation
   */
  public String serializeEddy(EddyProto.CRDTEddy eddy) {
    if (eddy == null) return null;
    try {
      return gson.toJson(proto2JsonElement(eddy));
    } catch (Exception e) {
      log.error("Failed to serialize eddy", e);
      throw new RuntimeException("Serialization failed", e);
    }
  }

  /**
   * Deserialize a JSON string to CRDTEddy proto.
   *
   * @param json the JSON string
   * @return deserialized CRDT eddy
   */
  public EddyProto.CRDTEddy deserializeEddy(String json) {
    if (json == null || json.isEmpty()) return null;
    try {
      JsonElement element = JsonParser.parseString(json);
      return jsonElement2CRDTEddy(element);
    } catch (Exception e) {
      log.error("Failed to deserialize eddy from JSON", e);
      throw new RuntimeException("Deserialization failed", e);
    }
  }

  /**
   * Serialize CRDTVector proto to JSON string.
   *
   * @param vector the vector to serialize
   * @return JSON string
   */
  public String serializeVector(EddyProto.CRDTVector vector) {
    if (vector == null) return null;
    try {
      return gson.toJson(proto2JsonElement(vector));
    } catch (Exception e) {
      log.error("Failed to serialize vector", e);
      throw new RuntimeException("Serialization failed", e);
    }
  }

  /**
   * Deserialize JSON string to CRDTVector proto.
   *
   * @param json the JSON string
   * @return deserialized vector
   */
  public EddyProto.CRDTVector deserializeVector(String json) {
    if (json == null || json.isEmpty()) return null;
    try {
      JsonElement element = JsonParser.parseString(json);
      return jsonElement2CRDTVector(element);
    } catch (Exception e) {
      log.error("Failed to deserialize vector from JSON", e);
      throw new RuntimeException("Deserialization failed", e);
    }
  }

  /**
   * Serialize ApprovalCounter proto to JSON string.
   *
   * @param counter the approval counter to serialize
   * @return JSON string
   */
  public String serializeApprovals(EddyProto.ApprovalCounter counter) {
    if (counter == null) return null;
    try {
      return gson.toJson(proto2JsonElement(counter));
    } catch (Exception e) {
      log.error("Failed to serialize approval counter", e);
      throw new RuntimeException("Serialization failed", e);
    }
  }

  /**
   * Deserialize JSON string to ApprovalCounter proto.
   *
   * @param json the JSON string
   * @return deserialized approval counter
   */
  public EddyProto.ApprovalCounter deserializeApprovals(String json) {
    if (json == null || json.isEmpty()) return null;
    try {
      JsonElement element = JsonParser.parseString(json);
      return jsonElement2ApprovalCounter(element);
    } catch (Exception e) {
      log.error("Failed to deserialize approval counter from JSON", e);
      throw new RuntimeException("Deserialization failed", e);
    }
  }

  // Proto to JSON: Convert proto to JSON using Protobuf's built-in JSON printer
  private JsonElement proto2JsonElement(Message proto) throws Exception {
    String json = com.google.protobuf.util.JsonFormat.printer()
      .omittingInsignificantWhitespace()
      .print(proto);
    return JsonParser.parseString(json);
  }

  // JSON to Proto: Reconstruct proto from JSON using Protobuf's parser
  private EddyProto.CRDTEddy jsonElement2CRDTEddy(JsonElement element)
    throws Exception {
    EddyProto.CRDTEddy.Builder builder = EddyProto.CRDTEddy.newBuilder();
    com.google.protobuf.util.JsonFormat.parser()
      .merge(element.toString(), builder);
    return builder.build();
  }

  private EddyProto.CRDTVector jsonElement2CRDTVector(JsonElement element)
    throws Exception {
    EddyProto.CRDTVector.Builder builder = EddyProto.CRDTVector.newBuilder();
    com.google.protobuf.util.JsonFormat.parser()
      .merge(element.toString(), builder);
    return builder.build();
  }

  private EddyProto.ApprovalCounter jsonElement2ApprovalCounter(
    JsonElement element
  ) throws Exception {
    EddyProto.ApprovalCounter.Builder builder =
      EddyProto.ApprovalCounter.newBuilder();
    com.google.protobuf.util.JsonFormat.parser()
      .merge(element.toString(), builder);
    return builder.build();
  }
}
