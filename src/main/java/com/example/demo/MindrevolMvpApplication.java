package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling; // Thêm import này

@SpringBootApplication
@EnableAsync
@EnableScheduling // Thêm annotation này để kích hoạt scheduling
public class MindrevolMvpApplication {

	public static void main(String[] args) {
		SpringApplication.run(MindrevolMvpApplication.class, args);
	}

}