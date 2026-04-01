package com.bookie.config;

import com.bookie.service.EmailParserTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailParserConfig {

  @Bean
  public ChatClient emailParserChatClient(ChatClient.Builder builder, EmailParserTools tools) {
    return builder.defaultAdvisors(new SimpleLoggerAdvisor()).defaultTools(tools).build();
  }
}
