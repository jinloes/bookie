package com.bookie.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Reads and writes Windows Credential Manager generic credentials. Each item is stored under the
 * target name {@code <service>:<account>} with the secret encoded as UTF-16LE, the same layout
 * {@code cmdkey /generic} and the Credential Manager UI use.
 */
public class WindowsCredentialStore implements SecretStore {

  /** The native Credential Manager calls; replaceable in tests so no real store is touched. */
  interface CredentialApi {
    Optional<byte[]> read(String targetName);

    boolean write(String targetName, String userName, byte[] secret);
  }

  private final CredentialApi api;

  public WindowsCredentialStore() {
    this(new JnaCredentialApi());
  }

  WindowsCredentialStore(CredentialApi api) {
    this.api = api;
  }

  static String targetName(String service, String account) {
    return service + ":" + account;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public Optional<String> read(String service, String account) {
    try {
      return api.read(targetName(service, account))
          .map(
              bytes -> {
                String secret = new String(bytes, StandardCharsets.UTF_16LE);
                Arrays.fill(bytes, (byte) 0);
                return secret.strip();
              })
          .filter(StringUtils::isNotBlank);
    } catch (RuntimeException | LinkageError e) {
      return Optional.empty();
    }
  }

  @Override
  public boolean write(String service, String account, String secret) {
    if (StringUtils.isEmpty(secret)) {
      return false;
    }
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_16LE);
    try {
      return api.write(targetName(service, account), account, bytes);
    } catch (RuntimeException | LinkageError e) {
      return false;
    } finally {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  @Override
  public String displayName() {
    return "Windows Credential Manager";
  }

  @Override
  public String storeCommand(String service, String account) {
    return "cmdkey /generic:%s /user:%s /pass".formatted(targetName(service, account), account);
  }
}
