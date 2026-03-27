package com.bookie;

import com.bookie.config.OutlookProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OutlookProperties.class)
public class BookieApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookieApplication.class, args);
	}

}
