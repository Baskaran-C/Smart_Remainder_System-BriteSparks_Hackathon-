package com.example.SmartRemainderSystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartRemainderSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartRemainderSystemApplication.class, args);
	}

}
