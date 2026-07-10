package com.bookie.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class TokenCacheCrypto {

  private static final String ENV_KEY = "BOOKIE_TOKEN_ENCRYPTION_KEY";
  private static final String KEYCHAIN_SERVICE = "com.bookie.msal-token-cache";
  private static final String KEYCHAIN_ACCOUNT = "default";
  private static final String ENCRYPTED_PREFIX = "enc:v1:";
  private static final int KEY_BYTES = 32;
  private static final int IV_BYTES = 12;
  private static final int GCM_TAG_BITS = 128;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final SecretKey key;
  private final String configuredKey;

  TokenCacheCrypto(@Value("${bookie.token-cache.encryption-key:}") String configuredKey) {
    this.configuredKey = configuredKey;
    this.key = new SecretKeySpec(loadOrCreateKeyBytes(), "AES");
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

  private byte[] loadOrCreateKeyBytes() {
    if (StringUtils.isNotBlank(configuredKey)) {
      return decodeKey(configuredKey.trim(), ENV_KEY);
    }

    String envValue = System.getenv(ENV_KEY);
    if (StringUtils.isNotBlank(envValue)) {
      return decodeKey(envValue.trim(), ENV_KEY);
    }

    if (isMacOs()) {
      byte[] keyFromKeychain = readKeyFromMacKeychain();
      if (keyFromKeychain != null) {
        return keyFromKeychain;
      }
      byte[] generated = generateKeyBytes();
      if (writeKeyToMacKeychain(generated)) {
        return generated;
      }
      log.warn("Unable to persist token key to macOS keychain; falling back to local key file");
    }

    return readOrCreateLocalKeyFile();
  }

  private byte[] readOrCreateLocalKeyFile() {
    Path keyPath = dataDir().resolve("token-cache.key");
    try {
      Files.createDirectories(keyPath.getParent());
      if (Files.exists(keyPath)) {
        String content = Files.readString(keyPath, StandardCharsets.UTF_8).trim();
        return decodeKey(content, keyPath.toString());
      }
      byte[] generated = generateKeyBytes();
      Files.writeString(
          keyPath, Base64.getEncoder().encodeToString(generated), StandardCharsets.UTF_8);
      return generated;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to initialize Outlook token encryption key", e);
    }
  }

  private byte[] readKeyFromMacKeychain() {
    List<String> command = new ArrayList<>();
    command.add("security");
    command.add("find-generic-password");
    command.add("-a");
    command.add(KEYCHAIN_ACCOUNT);
    command.add("-s");
    command.add(KEYCHAIN_SERVICE);
    command.add("-w");
    CommandResult result = runCommand(command);
    if (result.exitCode != 0 || StringUtils.isBlank(result.stdout)) {
      return null;
    }
    return decodeKey(result.stdout.trim(), "macOS keychain");
  }

  private boolean writeKeyToMacKeychain(byte[] keyBytes) {
    List<String> command = new ArrayList<>();
    command.add("security");
    command.add("add-generic-password");
    command.add("-U");
    command.add("-a");
    command.add(KEYCHAIN_ACCOUNT);
    command.add("-s");
    command.add(KEYCHAIN_SERVICE);
    command.add("-w");
    command.add(Base64.getEncoder().encodeToString(keyBytes));
    CommandResult result = runCommand(command);
    return result.exitCode == 0;
  }

  private CommandResult runCommand(List<String> command) {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    try {
      Process process = processBuilder.start();
      byte[] stdoutBytes = process.getInputStream().readAllBytes();
      byte[] stderrBytes = process.getErrorStream().readAllBytes();
      int exitCode = process.waitFor();
      return new CommandResult(
          exitCode,
          new String(stdoutBytes, StandardCharsets.UTF_8),
          new String(stderrBytes, StandardCharsets.UTF_8));
    } catch (IOException e) {
      log.warn("Token key command failed to start: {}", e.getMessage());
      return new CommandResult(1, "", e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Token key command interrupted");
      return new CommandResult(1, "", "interrupted");
    }
  }

  private Path dataDir() {
    String configured = System.getenv("BOOKIE_DATA_DIR");
    if (StringUtils.isNotBlank(configured)) {
      return Path.of(configured);
    }
    return Path.of(System.getProperty("user.home"), ".bookie");
  }

  private boolean isMacOs() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
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

  private record CommandResult(int exitCode, String stdout, String stderr) {}
}
