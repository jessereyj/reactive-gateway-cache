package com.learn.developer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReactiveGatewayCacheApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReactiveGatewayCacheApplication.class, args);
	}

}
