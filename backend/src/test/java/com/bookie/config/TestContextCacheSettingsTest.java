package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.SpringProperties;
import org.springframework.test.context.cache.ContextCache;

class TestContextCacheSettingsTest {

  @Nested
  class ContextCacheSize {

    @Test
    void capsCachedContextsToKeepTheTestHeapFromThrashing() {
      assertThat(SpringProperties.getProperty(ContextCache.MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME))
          .isEqualTo("8");
    }
  }
}
