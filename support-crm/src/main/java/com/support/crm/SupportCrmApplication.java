package com.support.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // to run thread in background
public class SupportCrmApplication {

	public static void main(String[] args) {
		SpringApplication.run(SupportCrmApplication.class, args);
	}

}
