package com.bookie.integrations.outlook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookie.util.SecretStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenCacheCryptoTest {

  private static final String STORE_ITEM =
      TokenCacheCrypto.SECRET_SERVICE + "/" + TokenCacheCrypto.SECRET_ACCOUNT;

  @TempDir Path dir;

  private FakeSecretStore store;
  private Path keyFile;

  @BeforeEach
  void setUp() {
    store = new FakeSecretStore();
    keyFile = dir.resolve(TokenCacheCrypto.KEY_FILE_NAME);
  }

  private static String newKey(int bytes) {
    return newKey(bytes, 0);
  }

  private static String newKey(int bytes, int seed) {
    byte[] key = new byte[bytes];
    for (int i = 0; i < bytes; i++) {
      key[i] = (byte) (i + seed);
    }
    return Base64.getEncoder().encodeToString(key);
  }

  private TokenCacheCrypto cryptoWithConfiguredKey(String key) {
    return new TokenCacheCrypto(key, new FakeSecretStore(), dir.resolve("unused.key"));
  }

  @Nested
  class KeyResolution {

    @Test
    void prefersTheConfiguredKeyOverTheSecretStore() {
      String configured = newKey(32);
      store.items.put(STORE_ITEM, newKey(32, 7));

      String encrypted = new TokenCacheCrypto(configured, store, keyFile).encrypt("cache");

      assertThat(cryptoWithConfiguredKey(configured).decrypt(encrypted)).isEqualTo("cache");
      assertThat(store.reads).isZero();
      assertThat(keyFile).doesNotExist();
    }

    @Test
    void usesTheKeyHeldInTheSecretStore() {
      String stored = newKey(32);
      store.items.put(STORE_ITEM, stored);

      String encrypted = new TokenCacheCrypto("  ", store, keyFile).encrypt("cache");

      assertThat(cryptoWithConfiguredKey(stored).decrypt(encrypted)).isEqualTo("cache");
      assertThat(keyFile).doesNotExist();
    }

    @Test
    void fallsBackToALegacyKeyFileWithoutWritingTheStore() throws IOException {
      String legacy = newKey(32);
      Files.writeString(keyFile, legacy + "\n");

      String encrypted = new TokenCacheCrypto(null, store, keyFile).encrypt("cache");

      assertThat(cryptoWithConfiguredKey(legacy).decrypt(encrypted)).isEqualTo("cache");
      assertThat(store.items).isEmpty();
    }

    @Test
    void skipsAnUnavailableStoreAndReadsTheKeyFile() throws IOException {
      store.available = false;
      String legacy = newKey(32);
      Files.writeString(keyFile, legacy);

      String encrypted = new TokenCacheCrypto("", store, keyFile).encrypt("cache");

      assertThat(cryptoWithConfiguredKey(legacy).decrypt(encrypted)).isEqualTo("cache");
      assertThat(store.reads).isZero();
    }

    @Test
    void generatesANewKeyIntoTheSecretStore() {
      String encrypted = new TokenCacheCrypto("", store, keyFile).encrypt("cache");

      assertThat(store.items).containsKey(STORE_ITEM);
      assertThat(Base64.getDecoder().decode(store.items.get(STORE_ITEM))).hasSize(32);
      assertThat(cryptoWithConfiguredKey(store.items.get(STORE_ITEM)).decrypt(encrypted))
          .isEqualTo("cache");
      assertThat(keyFile).doesNotExist();
    }

    @Test
    void writesTheGeneratedKeyToAFileWhenTheStoreRejectsIt() throws IOException {
      store.writable = false;

      String encrypted = new TokenCacheCrypto("", store, keyFile).encrypt("cache");

      String written = Files.readString(keyFile);
      assertThat(cryptoWithConfiguredKey(written).decrypt(encrypted)).isEqualTo("cache");
      assertThat(store.items).isEmpty();
    }

    @Test
    void writesTheGeneratedKeyToAFileWhenTheStoreIsUnavailable() throws IOException {
      store.available = false;
      Path nested = dir.resolve("nested").resolve(TokenCacheCrypto.KEY_FILE_NAME);

      String encrypted = new TokenCacheCrypto("", store, nested).encrypt("cache");

      assertThat(cryptoWithConfiguredKey(Files.readString(nested)).decrypt(encrypted))
          .isEqualTo("cache");
      assertThat(store.writes).isZero();
    }

    @Test
    void rejectsAConfiguredKeyThatIsNotBase64() {
      assertThatThrownBy(() -> cryptoWithConfiguredKey("not base64!"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid base64 token key from BOOKIE_TOKEN_ENCRYPTION_KEY");
    }

    @Test
    void rejectsAStoredKeyOfTheWrongLength() {
      store.items.put(STORE_ITEM, newKey(16));

      assertThatThrownBy(() -> new TokenCacheCrypto("", store, keyFile))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid token key length from Test Store");
    }

    @Test
    void reportsAnUnreadableKeyFile() throws IOException {
      Files.createDirectories(keyFile);

      assertThatThrownBy(() -> new TokenCacheCrypto("", store, keyFile))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to read Outlook token encryption key");
    }

    @Test
    void reportsAKeyFileThatCannotBeWritten() throws IOException {
      store.writable = false;
      Path blocker = Files.writeString(dir.resolve("blocker"), "file");

      assertThatThrownBy(() -> new TokenCacheCrypto("", store, blocker.resolve("token.key")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to initialize Outlook token encryption key");
    }

    @Test
    void springConstructorUsesTheConfiguredKeyWithoutTouchingTheOsStore() {
      String configured = newKey(32);

      String encrypted = new TokenCacheCrypto(configured, dir).encrypt("cache");

      assertThat(cryptoWithConfiguredKey(configured).decrypt(encrypted)).isEqualTo("cache");
      assertThat(dir.resolve(TokenCacheCrypto.KEY_FILE_NAME)).doesNotExist();
    }
  }

  @Nested
  class EncryptAndDecrypt {

    private TokenCacheCrypto crypto;

    @BeforeEach
    void setUp() {
      crypto = cryptoWithConfiguredKey(newKey(32));
    }

    @Test
    void roundTripsWithAnEncryptedPrefix() {
      String encrypted = crypto.encrypt("{\"cache\":\"json\"}");

      assertThat(encrypted).startsWith("enc:v1:").doesNotContain("cache");
      assertThat(crypto.decrypt(encrypted)).isEqualTo("{\"cache\":\"json\"}");
    }

    @Test
    void passesNullThrough() {
      assertThat(crypto.encrypt(null)).isNull();
      assertThat(crypto.decrypt(null)).isNull();
    }

    @Test
    void returnsLegacyPlaintextUnchanged() {
      assertThat(crypto.decrypt("{\"plain\":true}")).isEqualTo("{\"plain\":true}");
    }

    @Test
    void rejectsCiphertextFromADifferentKey() {
      String encrypted = cryptoWithConfiguredKey(newKey(32, 7)).encrypt("cache");

      assertThatThrownBy(() -> crypto.decrypt(encrypted))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to decrypt Outlook token cache");
    }

    @Test
    void rejectsAPayloadThatIsNotBase64() {
      assertThatThrownBy(() -> crypto.decrypt("enc:v1:not base64!"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to decrypt Outlook token cache");
    }

    @Test
    void rejectsAPayloadTooShortToHoldTheIv() {
      String tooShort = "enc:v1:" + Base64.getEncoder().encodeToString(new byte[12]);

      assertThatThrownBy(() -> crypto.decrypt(tooShort))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Encrypted Outlook token payload is invalid");
    }
  }

  private static final class FakeSecretStore implements SecretStore {
    final Map<String, String> items = new HashMap<>();
    boolean available = true;
    boolean writable = true;
    int reads;
    int writes;

    @Override
    public boolean isAvailable() {
      return available;
    }

    @Override
    public Optional<String> read(String service, String account) {
      reads++;
      return Optional.ofNullable(items.get(service + "/" + account));
    }

    @Override
    public boolean write(String service, String account, String secret) {
      writes++;
      if (writable) {
        items.put(service + "/" + account, secret);
      }
      return writable;
    }

    @Override
    public String displayName() {
      return "Test Store";
    }

    @Override
    public String storeCommand(String service, String account) {
      return "store " + service;
    }
  }
}
