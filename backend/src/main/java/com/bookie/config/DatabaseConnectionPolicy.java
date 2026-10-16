package com.bookie.config;

import com.bookie.util.SecretStore;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Secures the SQL Server connection: the password comes from the OS secret store (macOS Keychain or
 * Windows Credential Manager) and is set directly on the pool, so it never enters the Spring {@link
 * Environment}, logs or error messages; the URL must request a verified TLS connection.
 */
@Component
@Profile("sqlserver")
public class DatabaseConnectionPolicy implements BeanPostProcessor {

  static final String SECRET_SERVICE = "com.bookie.sqlserver";
  static final String AZURE_CERTIFICATE_HOST = "*.database.windows.net";

  private static final Pattern ENCRYPT_TRUE = Pattern.compile("(?i);\\s*encrypt\\s*=\\s*true\\b");
  private static final Pattern TRUST_SERVER_CERTIFICATE =
      Pattern.compile("(?i);\\s*trustServerCertificate\\s*=\\s*true\\b");
  private static final Pattern HOST_NAME_IN_CERTIFICATE =
      Pattern.compile("(?i);\\s*hostNameInCertificate\\s*=");

  private final Environment environment;
  private final SecretStore secretStore;

  @Autowired
  public DatabaseConnectionPolicy(Environment environment) {
    this(environment, SecretStore.forCurrentOs());
  }

  DatabaseConnectionPolicy(Environment environment, SecretStore secretStore) {
    this.environment = environment;
    this.secretStore = secretStore;
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (bean instanceof HikariDataSource dataSource) {
      dataSource.setJdbcUrl(secureUrl(dataSource.getJdbcUrl()));
      dataSource.setPassword(password(dataSource.getUsername()));
    }
    return bean;
  }

  String secureUrl(String url) {
    if (StringUtils.isBlank(url) || !url.toLowerCase(Locale.ROOT).startsWith("jdbc:sqlserver:")) {
      throw new IllegalStateException(
          "BOOKIE_DB_URL must be a jdbc:sqlserver: URL when BOOKIE_DB_VENDOR=sqlserver");
    }
    boolean allowInsecure =
        environment.getProperty("bookie.db.allow-insecure-tls", Boolean.class, false);
    boolean trustsAnyCertificate = TRUST_SERVER_CERTIFICATE.matcher(url).find();
    if (!allowInsecure) {
      if (!ENCRYPT_TRUE.matcher(url).find()) {
        throw new IllegalStateException(
            "BOOKIE_DB_URL must include encrypt=true so the connection uses TLS");
      }
      if (trustsAnyCertificate) {
        throw new IllegalStateException(
            "BOOKIE_DB_URL must not set trustServerCertificate=true; the server certificate"
                + " has to be verified");
      }
    }
    if (trustsAnyCertificate || HOST_NAME_IN_CERTIFICATE.matcher(url).find()) {
      return url;
    }
    return url + ";hostNameInCertificate=" + AZURE_CERTIFICATE_HOST;
  }

  String password(String username) {
    if (StringUtils.isBlank(username)) {
      throw new IllegalStateException(
          "BOOKIE_DB_USERNAME must be set when BOOKIE_DB_VENDOR=sqlserver");
    }
    if (environment.getProperty("bookie.db.allow-env-password", Boolean.class, false)) {
      String fromEnvironment = environment.getProperty("BOOKIE_DB_PASSWORD");
      if (StringUtils.isNotBlank(fromEnvironment)) {
        return fromEnvironment;
      }
    }
    return secretStore
        .read(SECRET_SERVICE, username)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    """
                    No SQL Server password was found in the %s for user '%s'.
                    Store it (you will be prompted, so it stays out of shell history) with:
                      %s"""
                        .formatted(
                            secretStore.displayName(),
                            username,
                            secretStore.storeCommand(SECRET_SERVICE, username))));
  }
}
