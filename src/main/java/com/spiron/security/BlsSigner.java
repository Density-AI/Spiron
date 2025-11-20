package com.spiron.security;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.mikuli.BLS12381;
import org.apache.tuweni.crypto.mikuli.KeyPair;
import org.apache.tuweni.crypto.mikuli.PublicKey;
import org.apache.tuweni.crypto.mikuli.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BLS Signer with keypair persistence for io.consensys.tuweni 2.7.2
 *
 * This implementation provides:
 * 1. Random keypair generation
 * 2. Keypair persistence to disk using Java serialization
 * 3. Keypair loading from keystore
 *
 * For production use, always use fromKeystore() to ensure the same keypair
 * is used across restarts.
 */
public class BlsSigner {

  private static final Logger log = LoggerFactory.getLogger(BlsSigner.class);
  private static final int DST_LENGTH = 48;

  private final KeyPair keyPair;

  /** Create a random BLS signer */
  public BlsSigner() {
    this.keyPair = KeyPair.random();
    log.info(
      "BLS signer created (random), pubKey(prefix)={}",
      shortHex(serializePublicKey(keyPair.publicKey()))
    );
  }

  /**
   * Create a BLS signer with seed parameter.
   * 
   * @deprecated Due to Tuweni library limitations, deterministic key generation from seed
   * is not supported. This constructor ignores the seed and generates a random keypair.
   * Use fromKeystore() for persistent keys instead.
   */
  @Deprecated
  public BlsSigner(byte[] seed) {
    // Tuweni's KeyPair class doesn't support construction from SecretKey,
    // so we cannot implement true deterministic key generation
    this.keyPair = KeyPair.random();
    log.warn(
      "BLS signer: seed parameter ignored (Tuweni limitation), random keypair generated. " +
      "Use BlsSigner.fromKeystore() for persistent keys. pubKey(prefix)={}",
      shortHex(serializePublicKey(keyPair.publicKey()))
    );
  }

  /**
   * Create a BLS signer from persisted keypair file using Java serialization,
   * or generate new one if not found.
   * This is the RECOMMENDED method for production use.
   */
  public static BlsSigner fromKeystore(Path keystoreDir, String nodeId) throws IOException {
    Objects.requireNonNull(keystoreDir, "keystoreDir cannot be null");
    Objects.requireNonNull(nodeId, "nodeId cannot be null");

    Files.createDirectories(keystoreDir);
    Path keyFile = keystoreDir.resolve(nodeId + ".keypair.ser");
    
    if (Files.exists(keyFile)) {
      log.info("Loading BLS keypair from {}", keyFile);
      try (FileInputStream fis = new FileInputStream(keyFile.toFile());
           ObjectInputStream ois = new ObjectInputStream(fis)) {
        
        KeyPair loadedKeyPair = (KeyPair) ois.readObject();
        BlsSigner signer = new BlsSigner(loadedKeyPair);
        log.info("Successfully loaded BLS keypair from keystore");
        return signer;
        
      } catch (Exception e) {
        log.error("Failed to load keypair from keystore, generating new one", e);
        BlsSigner signer = new BlsSigner();
        signer.saveToKeystore(keystoreDir, nodeId);
        return signer;
      }
    } else {
      log.info("No existing keypair found, generating new one");
      BlsSigner signer = new BlsSigner();
      signer.saveToKeystore(keystoreDir, nodeId);
      return signer;
    }
  }

  /**
   * Private constructor from existing KeyPair (used by keystore loading)
   */
  private BlsSigner(KeyPair keyPair) {
    this.keyPair = keyPair;
    log.info(
      "BLS signer loaded from keystore, pubKey(prefix)={}",
      shortHex(serializePublicKey(keyPair.publicKey()))
    );
  }

  /**
   * Save the keypair to disk using Java serialization
   */
  public void saveToKeystore(Path keystoreDir, String nodeId) throws IOException {
    Objects.requireNonNull(keystoreDir, "keystoreDir cannot be null");
    Objects.requireNonNull(nodeId, "nodeId cannot be null");

    Files.createDirectories(keystoreDir);
    Path keyFile = keystoreDir.resolve(nodeId + ".keypair.ser");
    
    try (FileOutputStream fos = new FileOutputStream(keyFile.toFile());
         ObjectOutputStream oos = new ObjectOutputStream(fos)) {
      
      oos.writeObject(keyPair);
      oos.flush();
      log.info("Saved BLS keypair to {}", keyFile);
      
    } catch (IOException e) {
      log.error("Failed to save keypair to keystore", e);
      throw e;
    }
    
    // Set file permissions to 600 (owner read/write only) on Unix systems
    try {
      Set<PosixFilePermission> perms = new HashSet<>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(keyFile, perms);
      log.info("Set keystore file permissions to 600");
    } catch (UnsupportedOperationException e) {
      log.warn("POSIX permissions not supported on this OS - keystore file may be world-readable");
    }
  }

  /**
   * Sign a message
   */
  public byte[] sign(byte[] message) {
    Objects.requireNonNull(message, "message cannot be null");
    Signature sig = BLS12381.sign(keyPair, Bytes.wrap(message), DST_LENGTH).signature();
    return sig.encode().toArray();
  }

  /**
   * Get the public key
   */
  public PublicKey publicKey() {
    return keyPair.publicKey();
  }

  /** Serialize public key to bytes */
  public static byte[] serializePublicKey(PublicKey pk) {
    Objects.requireNonNull(pk, "public key cannot be null");
    return pk.toByteArray();
  }

  /** Parse public key from bytes */
  public static PublicKey parsePublicKey(byte[] pkBytes) {
    Objects.requireNonNull(pkBytes, "public key bytes cannot be null");
    return PublicKey.fromBytes(pkBytes);
  }

  /** Aggregate multiple signatures */
  public static byte[] aggregate(List<byte[]> signatures) {
    if (signatures == null || signatures.isEmpty()) {
      throw new IllegalArgumentException("signatures list cannot be null or empty");
    }

    List<Signature> sigs = new ArrayList<>();
    for (byte[] sigBytes : signatures) {
      sigs.add(Signature.decode(Bytes.wrap(sigBytes)));
    }

    Signature aggregated = Signature.aggregate(sigs);
    return aggregated.encode().toArray();
  }

  /** Verify an aggregate signature */
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

      if (publicKeys.size() == 1) {
        return BLS12381.verify(
          publicKeys.get(0),
          aggSig,
          Bytes.wrap(messages.get(0)),
          DST_LENGTH
        );
      }

      for (int i = 0; i < publicKeys.size(); i++) {
        if (
          !BLS12381.verify(
            publicKeys.get(i),
            aggSig,
            Bytes.wrap(messages.get(i)),
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

  /** Get keystore directory path */
  public static Path getKeystoreDir(String dataDir) {
    return Paths.get(dataDir, "keystore");
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
