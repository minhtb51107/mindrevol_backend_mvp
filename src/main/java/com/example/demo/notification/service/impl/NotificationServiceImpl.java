package com.example.demo.notification.service.impl;

import com.example.demo.notification.dto.response.NotificationResponse;
import com.example.demo.notification.entity.Notification;
import com.example.demo.notification.mapper.NotificationMapper;
import com.example.demo.notification.repository.NotificationRepository;
import com.example.demo.notification.service.NotificationService;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    @Async // This method will run in a separate thread
    @Override
    @Transactional
    public void createNotification(User recipient, String message, String link) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .message(message)
                .link(link)
                .build();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationsForUser(String userEmail, Pageable pageable) {
        User user = findUserByEmail(userEmail);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(notificationMapper::toNotificationResponse);
    }

    @Override
    @Transactional
    public void markNotificationAsRead(Long notificationId, String userEmail) {
        User user = findUserByEmail(userEmail);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo với ID: " + notificationId));

        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi thông báo này.");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }
}