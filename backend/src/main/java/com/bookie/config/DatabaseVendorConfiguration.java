package com.bookie.config;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Activates the {@code sqlserver} profile when {@code BOOKIE_DB_VENDOR=sqlserver}. Runs before
 * config data loading so {@code application-sqlserver.properties} is picked up; without the
 * variable Bookie stays on H2.
 */
public class DatabaseVendorConfiguration implements EnvironmentPostProcessor, Ordered {

  static final String VENDOR_VARIABLE = "BOOKIE_DB_VENDOR";
  static final String SQL_SERVER = "sqlserver";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    String vendor = environment.getProperty(VENDOR_VARIABLE, "h2").trim().toLowerCase(Locale.ROOT);
    switch (vendor) {
      case SQL_SERVER -> environment.addActiveProfile(SQL_SERVER);
      case "h2", "" -> {
        // H2 is the default.
      }
      default ->
          throw new IllegalStateException(
              VENDOR_VARIABLE + " must be 'h2' or 'sqlserver', not '" + vendor + "'");
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
