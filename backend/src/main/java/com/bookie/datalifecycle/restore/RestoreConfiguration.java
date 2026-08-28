package com.bookie.datalifecycle.restore;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestoreConfiguration {

  @Bean
  RestorePaths restorePaths(
      @Value("${bookie.data-dir:${user.home}/.bookie}") String dataDirectory) {
    return new RestorePaths(Path.of(dataDirectory));
  }

  @Bean
  RestoreJournalStore restoreJournalStore(RestorePaths paths) {
    return new RestoreJournalStore(paths);
  }
}
