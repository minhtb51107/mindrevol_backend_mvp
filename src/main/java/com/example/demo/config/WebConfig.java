// File: src/main/java/com/example/demo/config/WebConfig.java

package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Áp dụng cấu hình CORS cho tất cả các đường dẫn dưới /api/v1/
                registry.addMapping("/api/v1/**")
                        // Cho phép yêu cầu từ origin của ứng dụng Vue.js
                        .allowedOrigins("http://localhost:5173")
                        // Các phương thức HTTP được phép
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        // Cho phép tất cả các header trong yêu cầu
                        .allowedHeaders("*")
                        // Cho phép trình duyệt gửi thông tin xác thực (như cookies)
                        .allowCredentials(true);
            }
        };
    }
}