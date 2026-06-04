package com.bookie.config;

import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.CopilotClientOptions;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CopilotSdkConfig {

  @Bean(destroyMethod = "close")
  public CopilotClient copilotClient(
      @Value("${ai.cli.path:}") String cliPath,
      @Value("${ai.auth.token:}") String authToken,
      @Value("${ai.auth.use-logged-in-user:true}") boolean useLoggedInUser) {
    CopilotClientOptions options = new CopilotClientOptions();
    if (StringUtils.isNotBlank(cliPath)) {
      options.setCliPath(cliPath);
    }
    if (StringUtils.isNotBlank(authToken)) {
      options.setGitHubToken(authToken);
    }
    options.setUseLoggedInUser(useLoggedInUser);
    return new CopilotClient(options);
  }
}
