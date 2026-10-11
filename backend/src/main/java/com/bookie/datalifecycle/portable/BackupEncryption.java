package com.bookie.datalifecycle.portable;

import com.bookie.util.MacKeychain;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts whole backup archives with AES-256-GCM under a key kept in the macOS Keychain. The file
 * header ({@code BOOKIEBAK}, format version, key fingerprint, IV) is authenticated as associated
 * data, so tampering with any byte fails decryption.
 */
@Component
public class BackupEncryption {

  public static final String FILE_EXTENSION = ".bookiebak";
  static final String KEYCHAIN_SERVICE = "com.bookie.backup-key";
  static final String KEYCHAIN_ACCOUNT = "default";
  static final String CONFIRMATION_FILE = "backup-key-confirmed";
  static final String KEY_PROBLEM = "backup key missing/mismatched";

  private static final byte[] MAGIC = "BOOKIEBAK".getBytes(StandardCharsets.US_ASCII);
  private static final byte FORMAT_VERSION = 1;
  private static final int FINGERPRINT_BYTES = 8;
  private static final int IV_BYTES = 12;
  private static final int KEY_BYTES = 32;
  private static final int GCM_TAG_BITS = 128;
  private static final int HEADER_BYTES = MAGIC.length + 1 + FINGERPRINT_BYTES + IV_BYTES;

  private final MacKeychain keychain;
  private final Path confirmationFile;
  private final SecureRandom random;

  @Autowired
  public BackupEncryption(@Value("${bookie.data-dir}") Path dataDirectory) {
    this(new MacKeychain(), dataDirectory, new SecureRandom());
  }

  public BackupEncryption(MacKeychain keychain, Path dataDirectory, SecureRandom random) {
    this.keychain = keychain;
    this.confirmationFile = dataDirectory.resolve(CONFIRMATION_FILE);
    this.random = random;
  }

  /** Encrypts {@code source} into {@code target}, generating the key on first use. */
  public void encrypt(Path source, Path target) throws IOException {
    byte[] key = loadOrCreateKey();
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
    header.put(MAGIC).put(FORMAT_VERSION).put(fingerprint(key)).put(iv);
    try {
      Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, iv, header.array());
      byte[] ciphertext = cipher.doFinal(Files.readAllBytes(source));
      byte[] output = Arrays.copyOf(header.array(), HEADER_BYTES + ciphertext.length);
      System.arraycopy(ciphertext, 0, output, HEADER_BYTES, ciphertext.length);
      Files.write(target, output);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not encrypt backup", e);
    }
  }

  /**
   * Decrypts {@code source} into {@code target}.
   *
   * @throws IllegalStateException with a "backup key missing/mismatched" message when the key is
   *     absent, differs from the one the backup was made with, or the file was tampered with
   */
  public void decrypt(Path source, Path target) throws IOException {
    byte[] data = Files.readAllBytes(source);
    if (!hasHeader(data)) {
      throw new IllegalStateException("Not an encrypted Bookie backup: " + source.getFileName());
    }
    byte[] key =
        readKey()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        KEY_PROBLEM + ": no key in Keychain service " + KEYCHAIN_SERVICE));
    byte[] fingerprint =
        Arrays.copyOfRange(data, MAGIC.length + 1, MAGIC.length + 1 + FINGERPRINT_BYTES);
    if (!MessageDigest.isEqual(fingerprint, fingerprint(key))) {
      throw new IllegalStateException(KEY_PROBLEM + ": the backup was encrypted with another key");
    }
    byte[] header = Arrays.copyOf(data, HEADER_BYTES);
    byte[] iv = Arrays.copyOfRange(header, HEADER_BYTES - IV_BYTES, HEADER_BYTES);
    try {
      Cipher cipher = cipher(Cipher.DECRYPT_MODE, key, iv, header);
      Files.write(target, cipher.doFinal(data, HEADER_BYTES, data.length - HEADER_BYTES));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(KEY_PROBLEM + ": the backup could not be authenticated", e);
    }
  }

  /** Whether {@code path} starts with the encrypted backup header. */
  public boolean isEncrypted(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return hasHeader(in.readNBytes(MAGIC.length + 1));
    }
  }

  /** Whether the user confirmed saving the current key. */
  public boolean isKeyConfirmed() throws IOException {
    Optional<byte[]> key = readKey();
    return key.isPresent()
        && Files.isRegularFile(confirmationFile)
        && Files.readString(confirmationFile).strip().equals(fingerprintHex(key.get()));
  }

  /** Records that the user saved the current key in their password manager. */
  public void confirmKey() throws IOException {
    Files.writeString(confirmationFile, fingerprintHex(loadOrCreateKey()));
  }

  /**
   * Returns the key in base64 for the user to save, generating it on first use.
   *
   * @throws IllegalStateException once the key has been confirmed, so it is shown only once
   */
  public String revealKey() throws IOException {
    if (isKeyConfirmed()) {
      throw new IllegalStateException(
          "The backup key was already confirmed and is not shown again");
    }
    return Base64.getEncoder().encodeToString(loadOrCreateKey());
  }

  private static boolean hasHeader(byte[] data) {
    return data.length > MAGIC.length
        && Arrays.equals(data, 0, MAGIC.length, MAGIC, 0, MAGIC.length)
        && data[MAGIC.length] == FORMAT_VERSION;
  }

  private Optional<byte[]> readKey() {
    return keychain
        .read(KEYCHAIN_SERVICE, KEYCHAIN_ACCOUNT)
        .map(encoded -> Base64.getDecoder().decode(encoded.strip()))
        .filter(bytes -> bytes.length == KEY_BYTES);
  }

  private synchronized byte[] loadOrCreateKey() {
    Optional<byte[]> existing = readKey();
    if (existing.isPresent()) {
      return existing.get();
    }
    byte[] key = new byte[KEY_BYTES];
    random.nextBytes(key);
    if (!keychain.write(
        KEYCHAIN_SERVICE, KEYCHAIN_ACCOUNT, Base64.getEncoder().encodeToString(key))) {
      throw new IllegalStateException(
          "Could not store the backup key in Keychain service " + KEYCHAIN_SERVICE);
    }
    return key;
  }

  private static Cipher cipher(int mode, byte[] key, byte[] iv, byte[] header)
      throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
    cipher.updateAAD(header);
    return cipher;
  }

  static byte[] fingerprint(byte[] key) {
    try {
      return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(key), FINGERPRINT_BYTES);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String fingerprintHex(byte[] key) {
    return HexFormat.of().formatHex(fingerprint(key));
  }
}
