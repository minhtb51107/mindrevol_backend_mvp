// File: src/main/java/com/example/demo/config/AsyncConfig.java

package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync // Bật tính năng xử lý bất đồng bộ của Spring
public class AsyncConfig {

    /**
     * Định nghĩa một bean Executor tùy chỉnh cho các tác vụ bất đồng bộ.
     * Bean này sẽ có tên là "taskExecutor" theo tên phương thức.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);      // Số luồng cơ bản trong pool
        executor.setMaxPoolSize(5);       // Số luồng tối đa
        executor.setQueueCapacity(500);   // Sức chứa của hàng đợi
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }
}