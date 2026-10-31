package com.bookie.integrations.outlook;

import com.bookie.util.SecretStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts the persisted MSAL token cache. The key comes from {@code BOOKIE_TOKEN_ENCRYPTION_KEY},
 * then the OS secret store, then a legacy {@code token-cache.key} file; otherwise a new key is
 * generated into the secret store. Machines sharing one database must resolve the same key.
 */
@Slf4j
@Component
class TokenCacheCrypto {

  static final String SECRET_SERVICE = "com.bookie.msal-token-cache";
  static final String SECRET_ACCOUNT = "default";
  static final String KEY_FILE_NAME = "token-cache.key";
  private static final String ENV_KEY = "BOOKIE_TOKEN_ENCRYPTION_KEY";
  private static final String ENCRYPTED_PREFIX = "enc:v1:";
  private static final int KEY_BYTES = 32;
  private static final int IV_BYTES = 12;
  private static final int GCM_TAG_BITS = 128;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final SecretKey key;

  @Autowired
  TokenCacheCrypto(
      @Value("${bookie.token-cache.encryption-key:}") String configuredKey,
      @Value("${bookie.data-dir}") Path dataDirectory) {
    this(configuredKey, SecretStore.forCurrentOs(), dataDirectory.resolve(KEY_FILE_NAME));
  }

  TokenCacheCrypto(String configuredKey, SecretStore secretStore, Path keyFile) {
    this.key = new SecretKeySpec(loadOrCreateKeyBytes(configuredKey, secretStore, keyFile), "AES");
  }

  String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      SECURE_RANDOM.nextBytes(iv);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] payload = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
      return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt Outlook token cache", e);
    }
  }

  String decrypt(String storedValue) {
    if (storedValue == null) {
      return null;
    }
    if (!storedValue.startsWith(ENCRYPTED_PREFIX)) {
      // Backward compatibility: existing plaintext cache rows are still readable.
      return storedValue;
    }
    String payloadBase64 = storedValue.substring(ENCRYPTED_PREFIX.length());
    try {
      byte[] payload = Base64.getDecoder().decode(payloadBase64);
      if (payload.length <= IV_BYTES) {
        throw new IllegalStateException("Encrypted Outlook token payload is invalid");
      }
      byte[] iv = new byte[IV_BYTES];
      byte[] encrypted = new byte[payload.length - IV_BYTES];
      System.arraycopy(payload, 0, iv, 0, IV_BYTES);
      System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] decrypted = cipher.doFinal(encrypted);
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Failed to decrypt Outlook token cache", e);
    }
  }

  private byte[] loadOrCreateKeyBytes(String configuredKey, SecretStore secretStore, Path keyFile) {
    if (StringUtils.isNotBlank(configuredKey)) {
      return decodeKey(configuredKey.trim(), ENV_KEY);
    }

    Optional<String> stored =
        secretStore.isAvailable()
            ? secretStore.read(SECRET_SERVICE, SECRET_ACCOUNT)
            : Optional.empty();
    if (stored.isPresent()) {
      return decodeKey(stored.get(), secretStore.displayName());
    }

    // Keys created before the secret store was used keep decrypting existing caches.
    if (Files.exists(keyFile)) {
      return readKeyFile(keyFile);
    }

    byte[] generated = generateKeyBytes();
    String encoded = Base64.getEncoder().encodeToString(generated);
    if (secretStore.isAvailable() && secretStore.write(SECRET_SERVICE, SECRET_ACCOUNT, encoded)) {
      return generated;
    }
    log.warn(
        "Unable to store the Outlook token key in {}; falling back to {}",
        secretStore.displayName(),
        keyFile);
    writeKeyFile(keyFile, encoded);
    return generated;
  }

  private byte[] readKeyFile(Path keyFile) {
    try {
      return decodeKey(
          Files.readString(keyFile, StandardCharsets.UTF_8).trim(), keyFile.toString());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read Outlook token encryption key", e);
    }
  }

  private void writeKeyFile(Path keyFile, String encoded) {
    try {
      Files.createDirectories(keyFile.toAbsolutePath().getParent());
      Files.writeString(keyFile, encoded, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to initialize Outlook token encryption key", e);
    }
  }

  private byte[] generateKeyBytes() {
    byte[] generated = new byte[KEY_BYTES];
    SECURE_RANDOM.nextBytes(generated);
    return generated;
  }

  private byte[] decodeKey(String keyBase64, String source) {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(keyBase64);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Invalid base64 token key from " + source, e);
    }
    if (decoded.length != KEY_BYTES) {
      throw new IllegalStateException(
          "Invalid token key length from " + source + " (expected 32 bytes)");
    }
    return decoded;
  }
}
