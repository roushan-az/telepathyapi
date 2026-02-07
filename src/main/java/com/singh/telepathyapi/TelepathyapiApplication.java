package com.singh.telepathyapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TelepathyapiApplication {

	public static void main(String[] args) {
		System.out.println("╔══════════════════════════════════════════╗");
		System.out.println("║   Signal-Style Secure Backend v1.0      ║");
		System.out.println("║   Zero Storage • E2E Encrypted           ║");
		System.out.println("║   WebRTC Video • Privacy First           ║");
		System.out.println("╚══════════════════════════════════════════╝");

		SpringApplication.run(TelepathyapiApplication.class, args);
	}
}
