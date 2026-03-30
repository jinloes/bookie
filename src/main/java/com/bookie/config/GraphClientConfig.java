package com.bookie.config;

import com.bookie.service.MsalTokenService;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphClientConfig {

  @Bean
  public GraphServiceClient graphServiceClient(MsalTokenService msalTokenService) {
    return new GraphServiceClient(
        (request, ctx) ->
            request.headers.tryAdd(
                "Authorization", "Bearer " + msalTokenService.getValidAccessToken()));
  }
}
