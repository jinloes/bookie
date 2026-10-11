package com.bookie.datalifecycle.portable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookie.util.MacKeychain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupEncryptionTest {

  @TempDir Path dir;

  private FakeKeychain keychain;
  private BackupEncryption encryption;
  private Path plain;

  @BeforeEach
  void setUp() throws IOException {
    keychain = new FakeKeychain();
    encryption = new BackupEncryption(keychain, dir, new SecureRandom());
    plain = Files.writeString(dir.resolve("plain.zip"), "ledger data");
  }

  private Path encrypted() throws IOException {
    Path target = dir.resolve("plain.zip.bookiebak");
    encryption.encrypt(plain, target);
    return target;
  }

  @Nested
  class EncryptAndDecrypt {

    @Test
    void roundTripsTheContent() throws IOException {
      Path target = encrypted();
      Path decrypted = dir.resolve("out.zip");

      encryption.decrypt(target, decrypted);

      assertThat(decrypted).hasContent("ledger data");
      assertThat(new String(Files.readAllBytes(target), StandardCharsets.ISO_8859_1))
          .doesNotContain("ledger data");
      assertThat(keychain.items).containsKey("com.bookie.backup-key/default");
    }

    @Test
    void reusesTheStoredKey() throws IOException {
      encrypted();
      String key = keychain.items.get("com.bookie.backup-key/default");

      encryption.encrypt(plain, dir.resolve("second.bookiebak"));

      assertThat(keychain.items.get("com.bookie.backup-key/default")).isEqualTo(key);
    }

    @Test
    void failsWhenTheKeyCannotBeStored() {
      keychain.writable = false;

      assertThatThrownBy(() -> encryption.encrypt(plain, dir.resolve("x.bookiebak")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Could not store the backup key");
    }

    @Test
    void rejectsAWrongKeyWithoutWritingOutput() throws IOException {
      Path target = encrypted();
      keychain.items.put(
          "com.bookie.backup-key/default", Base64.getEncoder().encodeToString(new byte[32]));
      Path out = dir.resolve("out.zip");

      assertThatThrownBy(() -> encryption.decrypt(target, out))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("backup key missing/mismatched")
          .hasMessageContaining("another key");
      assertThat(out).doesNotExist();
    }

    @Test
    void rejectsAMissingKey() throws IOException {
      Path target = encrypted();
      keychain.items.clear();

      assertThatThrownBy(() -> encryption.decrypt(target, dir.resolve("out.zip")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("backup key missing/mismatched")
          .hasMessageContaining("no key");
    }

    @Test
    void treatsAKeyOfTheWrongLengthAsMissing() throws IOException {
      Path target = encrypted();
      keychain.items.put(
          "com.bookie.backup-key/default", Base64.getEncoder().encodeToString(new byte[5]));

      assertThatThrownBy(() -> encryption.decrypt(target, dir.resolve("out.zip")))
          .hasMessageContaining("no key");
    }

    @Test
    void rejectsTamperedCiphertext() throws IOException {
      Path target = encrypted();
      byte[] bytes = Files.readAllBytes(target);
      bytes[bytes.length - 1] ^= 1;
      Files.write(target, bytes);
      Path out = dir.resolve("out.zip");

      assertThatThrownBy(() -> encryption.decrypt(target, out))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("could not be authenticated");
      assertThat(out).doesNotExist();
    }

    @Test
    void rejectsATamperedHeader() throws IOException {
      Path target = encrypted();
      byte[] bytes = Files.readAllBytes(target);
      bytes[bytes.length - 17 - "ledger data".length()] ^= 1; // last IV byte
      Files.write(target, bytes);

      assertThatThrownBy(() -> encryption.decrypt(target, dir.resolve("out.zip")))
          .hasMessageContaining("could not be authenticated");
    }

    @Test
    void rejectsAFileWithoutTheHeader() {
      assertThatThrownBy(() -> encryption.decrypt(plain, dir.resolve("out.zip")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Not an encrypted Bookie backup");
    }
  }

  @Nested
  class IsEncrypted {

    @Test
    void recognisesAnEncryptedFile() throws IOException {
      assertThat(encryption.isEncrypted(encrypted())).isTrue();
    }

    @Test
    void rejectsAShortFile() throws IOException {
      assertThat(encryption.isEncrypted(Files.writeString(dir.resolve("s"), "BOOK"))).isFalse();
    }

    @Test
    void rejectsADifferentMagic() throws IOException {
      assertThat(encryption.isEncrypted(plain)).isFalse();
    }

    @Test
    void rejectsAnUnknownFormatVersion() throws IOException {
      byte[] bytes = Files.readAllBytes(encrypted());
      bytes[9] = 2;

      assertThat(encryption.isEncrypted(Files.write(dir.resolve("v2"), bytes))).isFalse();
    }
  }

  @Nested
  class KeyConfirmation {

    @Test
    void isUnconfirmedWithoutAKey() throws IOException {
      assertThat(encryption.isKeyConfirmed()).isFalse();
    }

    @Test
    void isUnconfirmedUntilConfirmed() throws IOException {
      encryption.revealKey();

      assertThat(encryption.isKeyConfirmed()).isFalse();
    }

    @Test
    void confirmsTheCurrentKey() throws IOException {
      encryption.revealKey();
      encryption.confirmKey();

      assertThat(encryption.isKeyConfirmed()).isTrue();
    }

    @Test
    void becomesUnconfirmedWhenTheKeyChanges() throws IOException {
      encryption.confirmKey();
      keychain.items.put(
          "com.bookie.backup-key/default", Base64.getEncoder().encodeToString(new byte[32]));

      assertThat(encryption.isKeyConfirmed()).isFalse();
    }

    @Test
    void revealsTheStoredKeyInBase64() throws IOException {
      String revealed = encryption.revealKey();

      assertThat(Base64.getDecoder().decode(revealed)).hasSize(32);
      assertThat(keychain.items.get("com.bookie.backup-key/default")).isEqualTo(revealed);
    }

    @Test
    void refusesToRevealAConfirmedKey() throws IOException {
      encryption.confirmKey();

      assertThatThrownBy(() -> encryption.revealKey())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not shown again");
    }
  }

  @Test
  void springConstructorUsesTheDataDirectory() throws IOException {
    assertThat(new BackupEncryption(dir).isEncrypted(plain)).isFalse();
  }

  private static final class FakeKeychain extends MacKeychain {
    final Map<String, String> items = new HashMap<>();
    boolean writable = true;

    FakeKeychain() {
      super(command -> new CommandResult(1, ""), "Mac OS X");
    }

    @Override
    public Optional<String> read(String service, String account) {
      return Optional.ofNullable(items.get(service + "/" + account));
    }

    @Override
    public boolean write(String service, String account, String secret) {
      if (writable) {
        items.put(service + "/" + account, secret);
      }
      return writable;
    }
  }
}
