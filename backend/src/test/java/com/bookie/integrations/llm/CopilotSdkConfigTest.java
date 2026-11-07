package com.bookie.integrations.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.copilot.CopilotClient;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CopilotSdkConfigTest {

  private final CopilotSdkConfig config = new CopilotSdkConfig();

  @Nested
  class ResolveCliPath {

    @TempDir Path dir;

    @Test
    void explicitPathWins() {
      assertThat(CopilotSdkConfig.resolveCliPath("/x/copilot", dir.toString(), false))
          .isEqualTo("/x/copilot");
    }

    @Test
    void returnsNullWhenPathIsBlank() {
      assertThat(CopilotSdkConfig.resolveCliPath("", null, false)).isNull();
    }

    @Test
    void findsExecutableOnPath() throws Exception {
      Path exe = Files.createFile(dir.resolve("copilot"));
      String path = File.pathSeparator + dir;

      assertThat(CopilotSdkConfig.resolveCliPath("", path, false))
          .isEqualTo(exe.toFile().getAbsolutePath());
    }

    @Test
    void findsWindowsExecutableOnPath() throws Exception {
      Path exe = Files.createFile(dir.resolve("copilot.cmd"));

      assertThat(CopilotSdkConfig.resolveCliPath("", dir.toString(), true))
          .isEqualTo(exe.toFile().getAbsolutePath());
    }

    @Test
    void returnsNullWhenNotOnPath() {
      assertThat(CopilotSdkConfig.resolveCliPath("", dir.toString(), false)).isNull();
    }
  }

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
