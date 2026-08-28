package com.bookie.integrations.outlook;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphClientConfig {

  @Bean
  public GraphServiceClient graphServiceClient(OutlookAuthorization outlookAuthorization) {
    return new GraphServiceClient(
        (request, ctx) ->
            request.headers.tryAdd(
                "Authorization", "Bearer " + outlookAuthorization.getValidAccessToken()));
  }
}
