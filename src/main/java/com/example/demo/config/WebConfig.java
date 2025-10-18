//// File: src/main/java/com/example/demo/config/WebConfig.java (File mới)
//package com.example.demo.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.config.annotation.CorsRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//@Configuration
//public class WebConfig {
//
//    @Bean
//    public WebMvcConfigurer corsConfigurer() {
//        return new WebMvcConfigurer() {
//            @Override
//            public void addCorsMappings(CorsRegistry registry) {
//                registry.addMapping("/api/v1/**") // Áp dụng cho tất cả các API
//                        .allowedOrigins("http://localhost:5173") // Cho phép frontend
//                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS") // Cho phép tất cả các method
//                        .allowedHeaders("*") // Cho phép tất cả các header
//                        .allowCredentials(true); // Cho phép gửi thông tin xác thực
//            }
//        };
//    }
//}