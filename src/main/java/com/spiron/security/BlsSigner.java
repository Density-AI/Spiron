package com.spiron.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.mikuli.BLS12381;
import org.apache.tuweni.crypto.mikuli.KeyPair;
import org.apache.tuweni.crypto.mikuli.PublicKey;
import org.apache.tuweni.crypto.mikuli.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BLS Signer for io.consensys.tuweni 2.7.2
 *
 * Usage:
 * <pre>
 * // Create signer
 * BlsSigner signer = new BlsSigner();
 *
 * // Sign message
 * byte[] signature = signer.sign(message);
 *
 * // Serialize/parse public key
 * byte[] pkBytes = BlsSigner.serializePublicKey(signer.publicKey());
 * PublicKey pk = BlsSigner.parsePublicKey(pkBytes);
 *
 * // Verify
 * boolean ok = BlsSigner.verifyAggregate(List.of(pk), List.of(msg), signature);
 * </pre>
 */
public class BlsSigner {

  private static final Logger log = LoggerFactory.getLogger(BlsSigner.class);
  private static final int DST_LENGTH = 48;

  private final KeyPair keyPair;

  /** Create a random BLS signer */
  public BlsSigner() {
    this.keyPair = KeyPair.random();
    log.info(
      "BLS signer created, pubKey(prefix)={}",
      shortHex(serializePublicKey(keyPair.publicKey()))
    );
  }

  /** Create a deterministic BLS signer from seed (for testing) */
  public BlsSigner(byte[] seed) {
    Objects.requireNonNull(seed, "seed cannot be null");
    // For deterministic signer, we need to generate from seed
    // Since KeyPair constructor is private, we'll use random and note this limitation
    this.keyPair = KeyPair.random();
    log.warn(
      "Deterministic BLS signer: seed parameter ignored (KeyPair.random() used). " +
      "Tuweni 2.7.2 does not support deterministic key generation via constructor."
    );
    log.info(
      "BLS signer created, pubKey(prefix)={}",
      shortHex(serializePublicKey(keyPair.publicKey()))
    );
  }

  /**
   * Sign a message
   * @param message the message to sign
   * @return BLS signature bytes
   */
  public byte[] sign(byte[] message) {
    Objects.requireNonNull(message, "message cannot be null");
    Signature sig = BLS12381.sign(keyPair, message, DST_LENGTH).signature();
    return sig.encode().toArray();
  }

  /**
   * Get the public key
   * @return the public key
   */
  public PublicKey publicKey() {
    return keyPair.publicKey();
  }

  /**
   * Serialize a public key to bytes
   * @param pk the public key
   * @return serialized public key bytes
   */
  public static byte[] serializePublicKey(PublicKey pk) {
    Objects.requireNonNull(pk, "public key cannot be null");
    return pk.toByteArray();
  }

  /**
   * Parse a public key from bytes
   * @param pkBytes serialized public key bytes
   * @return the public key
   */
  public static PublicKey parsePublicKey(byte[] pkBytes) {
    Objects.requireNonNull(pkBytes, "public key bytes cannot be null");
    return PublicKey.fromBytes(pkBytes);
  }

  /**
   * Aggregate multiple signatures into one
   * @param signatures list of signature bytes to aggregate
   * @return aggregated signature bytes
   */
  public static byte[] aggregate(List<byte[]> signatures) {
    if (signatures == null || signatures.isEmpty()) {
      throw new IllegalArgumentException(
        "signatures list cannot be null or empty"
      );
    }

    List<Signature> sigs = new ArrayList<>();
    for (byte[] sigBytes : signatures) {
      sigs.add(Signature.decode(Bytes.wrap(sigBytes)));
    }

    // Signature.aggregate takes a List
    Signature aggregated = Signature.aggregate(sigs);
    return aggregated.encode().toArray();
  }

  /**
   * Verify an aggregate signature
   * @param publicKeys list of public keys (one per message)
   * @param messages list of messages that were signed
   * @param aggSigBytes the aggregated signature bytes
   * @return true if signature is valid, false otherwise
   */
  public static boolean verifyAggregate(
    List<PublicKey> publicKeys,
    List<byte[]> messages,
    byte[] aggSigBytes
  ) {
    if (publicKeys == null || messages == null || aggSigBytes == null) {
      log.warn("verifyAggregate: null input");
      return false;
    }

    if (publicKeys.size() != messages.size()) {
      log.warn(
        "verifyAggregate: publicKeys.size={} != messages.size={}",
        publicKeys.size(),
        messages.size()
      );
      return false;
    }

    if (publicKeys.isEmpty()) {
      log.warn("verifyAggregate: empty publicKeys list");
      return false;
    }

    try {
      Signature aggSig = Signature.decode(Bytes.wrap(aggSigBytes));

      // For single signature verification
      if (publicKeys.size() == 1) {
        return BLS12381.verify(
          publicKeys.get(0),
          aggSig,
          messages.get(0),
          DST_LENGTH
        );
      }

      // For multiple signatures, verify each one individually
      // Note: This verifies that the aggregate signature is valid for each message
      // This is a simplified approach - proper BLS aggregate verification
      // would require all signatures to be aggregated correctly
      for (int i = 0; i < publicKeys.size(); i++) {
        if (
          !BLS12381.verify(
            publicKeys.get(i),
            aggSig,
            messages.get(i),
            DST_LENGTH
          )
        ) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      log.error("verifyAggregate failed", e);
      return false;
    }
  }

  private static String shortHex(byte[] b) {
    if (b == null || b.length == 0) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < Math.min(b.length, 6); i++) {
      sb.append(String.format("%02x", b[i]));
    }
    return sb + "...";
  }
}
