package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling; // Thêm import này

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling // Thêm annotation này để kích hoạt scheduling
public class MindrevolMvpApplication {

	public static void main(String[] args) {
		SpringApplication.run(MindrevolMvpApplication.class, args);
	}

	@PostConstruct
    public void init() {
        // Thiết lập múi giờ mặc định là GMT+7 (Asia/Ho_Chi_Minh)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        System.out.println("Spring boot application running in UTC timezone :" + new java.util.Date());   
    }
}