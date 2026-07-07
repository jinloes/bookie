package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.copilot.CopilotClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CopilotSdkConfigTest {

  private final CopilotSdkConfig config = new CopilotSdkConfig();

  @Nested
  class CopilotClientBean {

    @Test
    void createsClientWhenOnlyLoggedInAuthIsUsed() {
      CopilotClient client = config.copilotClient("", "", true);

      assertThat(client).isNotNull();
      client.close();
    }

    @Test
    void createsClientWhenTokenAndCliPathAreProvided() {
      CopilotClient client = config.copilotClient("/tmp/copilot", "test-token", false);

      assertThat(client).isNotNull();
      client.close();
    }
  }
}
