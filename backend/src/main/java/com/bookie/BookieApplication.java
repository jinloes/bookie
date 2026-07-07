package com.bookie;

import com.bookie.config.OutlookProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(OutlookProperties.class)
@EnableRetry
@EnableScheduling
@EnableAsync
public class BookieApplication {

  public static void main(String[] args) {
    SpringApplication.run(BookieApplication.class, args);
  }
}
