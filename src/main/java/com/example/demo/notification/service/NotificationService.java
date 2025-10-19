package com.example.demo.notification.service;

import com.example.demo.notification.dto.response.NotificationResponse;
import com.example.demo.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    void createNotification(User recipient, String message, String link);
    Page<NotificationResponse> getNotificationsForUser(String userEmail, Pageable pageable);
    void markNotificationAsRead(Long notificationId, String userEmail);
    void markAllAsReadForUser(String userEmail); // Thêm phương thức này
}