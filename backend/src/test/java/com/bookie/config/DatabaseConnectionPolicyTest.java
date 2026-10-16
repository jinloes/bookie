package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.util.SecretStore;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseConnectionPolicyTest {

  private static final String AZURE_URL =
      "jdbc:sqlserver://bookie.database.windows.net:1433;databaseName=bookie;encrypt=true";

  private final SecretStore keychain = mock(SecretStore.class);
  private final MockEnvironment environment = new MockEnvironment();
  private final DatabaseConnectionPolicy policy =
      new DatabaseConnectionPolicy(environment, keychain);

  @Nested
  class PostProcessBeforeInitialization {

    @Test
    void securesTheHikariPoolWithTheKeychainPassword() {
      when(keychain.read("com.bookie.sqlserver", "bookie_app")).thenReturn(Optional.of("pw"));
      HikariDataSource dataSource = new HikariDataSource();
      dataSource.setJdbcUrl(AZURE_URL);
      dataSource.setUsername("bookie_app");

      Object result = policy.postProcessBeforeInitialization(dataSource, "dataSource");

      assertThat(result).isSameAs(dataSource);
      assertThat(dataSource.getJdbcUrl())
          .isEqualTo(AZURE_URL + ";hostNameInCertificate=*.database.windows.net");
      assertThat(dataSource.getPassword()).isEqualTo("pw");
    }

    @Test
    void leavesOtherBeansAlone() {
      Object bean = new Object();

      assertThat(policy.postProcessBeforeInitialization(bean, "other")).isSameAs(bean);
      verify(keychain, never()).read(anyString(), anyString());
    }
  }

  @Nested
  class SecureUrl {

    @Test
    void addsTheAzureCertificateHost() {
      assertThat(policy.secureUrl(AZURE_URL))
          .isEqualTo(AZURE_URL + ";hostNameInCertificate=*.database.windows.net");
    }

    @Test
    void keepsAnExplicitCertificateHost() {
      String url = AZURE_URL + ";hostNameInCertificate=*.example.net";

      assertThat(policy.secureUrl(url)).isEqualTo(url);
    }

    @Test
    void rejectsABlankUrl() {
      assertThatThrownBy(() -> policy.secureUrl(" "))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("jdbc:sqlserver:");
    }

    @Test
    void rejectsANonSqlServerUrl() {
      assertThatThrownBy(() -> policy.secureUrl("jdbc:h2:mem:x"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("jdbc:sqlserver:");
    }

    @Test
    void rejectsAnUnencryptedUrl() {
      assertThatThrownBy(() -> policy.secureUrl("jdbc:sqlserver://h;databaseName=b"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("encrypt=true");
    }

    @Test
    void rejectsTrustingAnyCertificate() {
      assertThatThrownBy(() -> policy.secureUrl(AZURE_URL + ";trustServerCertificate=true"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("trustServerCertificate");
    }

    @Test
    void allowsInsecureTlsOnlyWhenExplicitlyEnabled() {
      environment.setProperty("bookie.db.allow-insecure-tls", "true");
      String url = "jdbc:sqlserver://localhost:14333;databaseName=bk;trustServerCertificate=true";

      assertThat(policy.secureUrl(url)).isEqualTo(url);
    }

    @Test
    void stillAddsTheCertificateHostWhenInsecureTlsIsAllowedButNotUsed() {
      environment.setProperty("bookie.db.allow-insecure-tls", "true");
      String url = "jdbc:sqlserver://h;databaseName=b";

      assertThat(policy.secureUrl(url))
          .isEqualTo(url + ";hostNameInCertificate=*.database.windows.net");
    }
  }

  @Nested
  class Password {

    @Test
    void readsTheKeychain() {
      when(keychain.read("com.bookie.sqlserver", "bookie_app")).thenReturn(Optional.of("pw"));

      assertThat(policy.password("bookie_app")).isEqualTo("pw");
    }

    @Test
    void ignoresAnEnvironmentPasswordUnlessAllowed() {
      environment.setProperty("BOOKIE_DB_PASSWORD", "from-env");
      when(keychain.read("com.bookie.sqlserver", "bookie_app")).thenReturn(Optional.of("pw"));

      assertThat(policy.password("bookie_app")).isEqualTo("pw");
    }

    @Test
    void usesTheEnvironmentPasswordWhenAllowed() {
      environment.setProperty("bookie.db.allow-env-password", "true");
      environment.setProperty("BOOKIE_DB_PASSWORD", "from-env");

      assertThat(policy.password("bookie_app")).isEqualTo("from-env");
      verify(keychain, never()).read(anyString(), anyString());
    }

    @Test
    void fallsBackToTheKeychainWhenTheAllowedEnvironmentPasswordIsBlank() {
      environment.setProperty("bookie.db.allow-env-password", "true");
      when(keychain.read("com.bookie.sqlserver", "bookie_app")).thenReturn(Optional.of("pw"));

      assertThat(policy.password("bookie_app")).isEqualTo("pw");
    }

    @Test
    void explainsHowToStoreAMissingPasswordWithoutRevealingSecrets() {
      environment.setProperty("BOOKIE_DB_PASSWORD", "leaked-secret");
      when(keychain.read("com.bookie.sqlserver", "bookie_app")).thenReturn(Optional.empty());
      when(keychain.displayName()).thenReturn("Test Store");
      when(keychain.storeCommand("com.bookie.sqlserver", "bookie_app")).thenReturn("store-it");

      assertThatThrownBy(() -> policy.password("bookie_app"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No SQL Server password was found in the Test Store")
          .hasMessageContaining("store-it")
          .hasMessageNotContaining("leaked-secret");
    }

    @Test
    void requiresAUsername() {
      assertThatThrownBy(() -> policy.password(""))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("BOOKIE_DB_USERNAME");
    }
  }

  @Test
  void publicConstructorUsesTheRealKeychain() {
    assertThat(new DatabaseConnectionPolicy(new MockEnvironment())).isNotNull();
  }
}
