package com.bookie.integrations.llm;

import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.CopilotClientOptions;
import java.io.File;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "ai.provider", havingValue = "copilot", matchIfMissing = true)
public class CopilotSdkConfig {

  @Bean(destroyMethod = "close")
  public CopilotClient copilotClient(
      @Value("${ai.cli.path:}") String cliPath,
      @Value("${ai.auth.token:}") String authToken,
      @Value("${ai.auth.use-logged-in-user:true}") boolean useLoggedInUser) {
    CopilotClientOptions options = new CopilotClientOptions();
    String resolved = resolveCliPath(cliPath, System.getenv("PATH"), isWindows());
    if (resolved != null) {
      log.info("Using Copilot CLI at {}", resolved);
      options.setCliPath(resolved);
    } else {
      log.warn(
          "No Copilot CLI found on PATH and ai.cli.path is unset; relying on the SDK's bundled"
              + " runtime, which may be missing. Set AI_CLI_PATH to a copilot executable.");
    }
    if (StringUtils.isNotBlank(authToken)) {
      options.setGitHubToken(authToken);
    }
    options.setUseLoggedInUser(useLoggedInUser);
    return new CopilotClient(options);
  }

  /** Explicit path wins; otherwise the first {@code copilot} executable on PATH, or null. */
  static String resolveCliPath(String configured, String pathEnv, boolean windows) {
    if (StringUtils.isNotBlank(configured)) {
      return configured;
    }
    if (StringUtils.isBlank(pathEnv)) {
      return null;
    }
    List<String> names =
        windows ? List.of("copilot.exe", "copilot.cmd") : List.of("copilot");
    for (String dir : pathEnv.split(File.pathSeparator)) {
      if (StringUtils.isBlank(dir)) {
        continue;
      }
      for (String name : names) {
        File candidate = new File(dir.trim(), name);
        if (candidate.isFile()) {
          return candidate.getAbsolutePath();
        }
      }
    }
    return null;
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }
}
