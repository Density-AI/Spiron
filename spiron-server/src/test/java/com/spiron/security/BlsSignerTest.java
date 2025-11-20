package com.spiron.security;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.tuweni.crypto.mikuli.PublicKey;
import org.junit.jupiter.api.Test;

class BlsSignerTest {

  @Test
  void sign_and_verify_single_ok() {
    BlsSigner s = new BlsSigner();
    byte[] msg = "spiron:hello".getBytes(StandardCharsets.UTF_8);

    byte[] sig = s.sign(msg);
    assertNotNull(sig);
    assertTrue(sig.length > 0);

    boolean ok = BlsSigner.verifyAggregate(
      List.of(s.publicKey()),
      List.of(msg),
      sig
    );
    assertTrue(ok, "single-signer verification should succeed");
  }

  @Test
  void public_key_serialize_parse_roundtrip() {
    BlsSigner s = new BlsSigner();

    byte[] pkBytes = BlsSigner.serializePublicKey(s.publicKey());
    assertNotNull(pkBytes);
    assertTrue(pkBytes.length > 0);

    PublicKey parsed = BlsSigner.parsePublicKey(pkBytes);
    assertNotNull(parsed);

    // round-trip bytes should be identical
    assertArrayEquals(pkBytes, BlsSigner.serializePublicKey(parsed));
  }

  @Test
  void aggregate_of_single_signature_matches_original_encoding() {
    BlsSigner s = new BlsSigner();
    byte[] msg = "spiron:single".getBytes(StandardCharsets.UTF_8);

    byte[] sig = s.sign(msg);
    byte[] agg = BlsSigner.aggregate(List.of(sig));

    assertArrayEquals(
      sig,
      agg,
      "aggregating a single signature should be identity"
    );
    assertTrue(
      BlsSigner.verifyAggregate(List.of(s.publicKey()), List.of(msg), agg)
    );
  }

  @Test
  void aggregate_two_signatures_currently_not_verified_by_simplified_impl() {
    // NOTE: Your current verifyAggregate() verifies each message against the same aggregate signature.
    // That is NOT true BLS multi-agg verification, so we expect it to return false here.
    BlsSigner s1 = new BlsSigner();
    BlsSigner s2 = new BlsSigner();

    byte[] m1 = "spiron:m1".getBytes(StandardCharsets.UTF_8);
    byte[] m2 = "spiron:m2".getBytes(StandardCharsets.UTF_8);

    byte[] sig1 = s1.sign(m1);
    byte[] sig2 = s2.sign(m2);
    byte[] agg = BlsSigner.aggregate(List.of(sig1, sig2));

    boolean ok = BlsSigner.verifyAggregate(
      List.of(s1.publicKey(), s2.publicKey()),
      List.of(m1, m2),
      agg
    );
    assertFalse(
      ok,
      "with current simplified verifyAggregate, multi-agg should not pass"
    );
  }

  @Test
  void verify_rejects_size_mismatch() {
    BlsSigner s = new BlsSigner();
    byte[] msg = "spiron:one".getBytes(StandardCharsets.UTF_8);
    byte[] sig = s.sign(msg);

    // 1 pubkey, 2 messages -> should reject
    boolean ok1 = BlsSigner.verifyAggregate(
      List.of(s.publicKey()),
      List.of(msg, msg),
      sig
    );
    assertFalse(ok1);

    // 2 pubkeys, 1 message -> should reject
    boolean ok2 = BlsSigner.verifyAggregate(
      List.of(s.publicKey(), s.publicKey()),
      List.of(msg),
      sig
    );
    assertFalse(ok2);
  }

  @Test
  void verify_rejects_nulls_and_empty() {
    BlsSigner s = new BlsSigner();
    byte[] msg = "spiron:nulls".getBytes(StandardCharsets.UTF_8);
    byte[] sig = s.sign(msg);

    assertFalse(BlsSigner.verifyAggregate(null, List.of(msg), sig));
    assertFalse(BlsSigner.verifyAggregate(List.of(s.publicKey()), null, sig));
    assertFalse(
      BlsSigner.verifyAggregate(List.of(s.publicKey()), List.of(msg), null)
    );
    assertFalse(BlsSigner.verifyAggregate(List.of(), List.of(), new byte[0]));
  }

  @Test
  void verify_rejects_corrupted_signature() {
    BlsSigner s = new BlsSigner();
    byte[] msg = "spiron:auth".getBytes(StandardCharsets.UTF_8);
    byte[] sig = s.sign(msg);

    // flip a byte
    byte[] bad = sig.clone();
    bad[Math.min(10, bad.length - 1)] ^= 0x7F;

    boolean ok = BlsSigner.verifyAggregate(
      List.of(s.publicKey()),
      List.of(msg),
      bad
    );
    assertFalse(ok, "corrupted signature must fail verification");
  }

  @Test
  void deterministic_ctor_works_but_seed_is_ignored_in_2_7_2() {
    // Your implementation logs that the seed is ignored (KeyPair.random() used).
    byte[] seed = "any-seed-ignored-in-2.7.2".getBytes(StandardCharsets.UTF_8);
    BlsSigner s = new BlsSigner(seed);

    byte[] msg = "spiron:seeded".getBytes(StandardCharsets.UTF_8);
    byte[] sig = s.sign(msg);

    assertNotNull(sig);
    assertTrue(sig.length > 0);
    assertTrue(
      BlsSigner.verifyAggregate(List.of(s.publicKey()), List.of(msg), sig)
    );
  }
}
