package com.bookie.datalifecycle.restore;

import com.bookie.datalifecycle.migration.MigrationIntegrityException;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

public class RestoreBootstrapListener
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    String dataDirectory = event.getEnvironment().getRequiredProperty("bookie.data-dir");
    RestorePaths paths = new RestorePaths(Path.of(dataDirectory));
    try {
      RestoreBootstrap.activatePendingRestore(paths, new RestoreJournalStore(paths));
    } catch (IOException | RuntimeException e) {
      throw new MigrationIntegrityException("Could not safely activate staged restore", e);
    }
  }
}
