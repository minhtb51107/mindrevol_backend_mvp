package com.example.demo.notification.mapper;

import com.example.demo.notification.dto.response.NotificationResponse;
import com.example.demo.notification.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {
    public NotificationResponse toNotificationResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .read(notification.isRead())
                .link(notification.getLink())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}