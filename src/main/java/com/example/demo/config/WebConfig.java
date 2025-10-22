// File: src/main/java/com/example/demo/config/WebConfig.java
package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value; // Thêm import
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry; // Thêm import
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer { // Implement WebMvcConfigurer

    @Value("${file.upload-dir}") // Inject đường dẫn từ properties
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Cấu hình để truy cập file upload qua URL /uploads/**
        // Ví dụ: http://localhost:8080/uploads/ten_file.jpg
        // Sẽ trỏ đến file trong thư mục uploadDir
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/"); // Phải có "file:" và dấu "/" cuối cùng
    }

    // Cấu hình CORS đã chuyển sang SecurityConfig, không cần ở đây nữa
    // @Bean
    // public WebMvcConfigurer corsConfigurer() { ... }
}