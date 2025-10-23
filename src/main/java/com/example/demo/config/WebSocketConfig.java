package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered; // *** THÊM IMPORT ***
import org.springframework.core.annotation.Order; // *** THÊM IMPORT ***
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.example.demo.config.security.WebSocketAuthInterceptor; // *** THÊM IMPORT ***

import lombok.RequiredArgsConstructor; // *** THÊM IMPORT ***


@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor // *** THÊM ANNOTATION ***
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // *** Đảm bảo interceptor chạy trước các interceptor bảo mật mặc định ***
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor; // *** INJECT INTERCEPTOR ***

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        // config.setApplicationDestinationPrefixes("/app"); // Bỏ comment nếu cần gửi message từ client lên server xử lý
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173")
                .withSockJS();
    }

    // *** THÊM PHƯƠNG THỨC NÀY ĐỂ ĐĂNG KÝ INTERCEPTOR ***
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
    // *** KẾT THÚC THÊM ***
}