package com.bookie;

import com.bookie.datalifecycle.restore.RestoreBootstrapListener;
import com.bookie.integrations.outlook.OutlookProperties;
import java.time.Clock;
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
    SpringApplication application = new SpringApplication(BookieApplication.class);
    // Restore bootstrap runs before an application context exists to inject its clock.
    application.addListeners(new RestoreBootstrapListener(Clock.systemDefaultZone()));
    application.run(args);
  }
}
