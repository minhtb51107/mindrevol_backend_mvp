// src/test/java/com/example/demo/notification/service/impl/NotificationServiceImplTest.java
package com.example.demo.notification.service.impl;

import com.example.demo.notification.dto.response.NotificationResponse;
import com.example.demo.notification.entity.Notification;
import com.example.demo.notification.mapper.NotificationMapper;
import com.example.demo.notification.repository.NotificationRepository;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Spy // Use Spy to test actual mapping logic
    private NotificationMapper notificationMapper = new NotificationMapper();

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User user1;
    private User user2;
    private Notification notification;

    @BeforeEach
    void setUp() {
        user1 = User.builder().id(1).email("user1@example.com").build();
        user2 = User.builder().id(2).email("user2@example.com").build();

        notification = Notification.builder()
                .id(1L)
                .recipient(user1)
                .message("Test notification")
                .link("/test")
                .read(false)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void createNotification_Success() {
        notificationService.createNotification(user1, "New message", "/link");

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());

        Notification savedNotification = notificationCaptor.getValue();
        assertEquals(user1, savedNotification.getRecipient());
        assertEquals("New message", savedNotification.getMessage());
        assertEquals("/link", savedNotification.getLink());
        assertFalse(savedNotification.isRead());
    }

    @Test
    void getNotificationsForUser_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notificationPage = new PageImpl<>(Collections.singletonList(notification), pageable, 1);

        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user1.getId(), pageable)).thenReturn(notificationPage);

        Page<NotificationResponse> result = notificationService.getNotificationsForUser(user1.getEmail(), pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(notification.getMessage(), result.getContent().get(0).getMessage());
        verify(notificationMapper).toNotificationResponse(notification); // Verify mapper was called
    }

     @Test
    void getNotificationsForUser_UserNotFound() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.getNotificationsForUser("notfound@example.com", pageable));
         verify(notificationRepository, never()).findByRecipientIdOrderByCreatedAtDesc(anyInt(), any());
    }

    @Test
    void markNotificationAsRead_Success() {
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertFalse(notification.isRead()); // Initially unread
        notificationService.markNotificationAsRead(notification.getId(), user1.getEmail());

        assertTrue(notification.isRead()); // Should be marked as read
        verify(notificationRepository).save(notification);
    }

     @Test
    void markNotificationAsRead_AlreadyRead() {
        notification.setRead(true); // Already read
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        notificationService.markNotificationAsRead(notification.getId(), user1.getEmail());

        assertTrue(notification.isRead()); // Still read
        verify(notificationRepository, never()).save(notification); // Should not save if already read
    }

    @Test
    void markNotificationAsRead_NotificationNotFound() {
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.markNotificationAsRead(99L, user1.getEmail()));
         verify(notificationRepository, never()).save(any());
    }

    @Test
    void markNotificationAsRead_AccessDenied() {
        when(userRepository.findByEmail(user2.getEmail())).thenReturn(Optional.of(user2)); // Different user
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertThrows(AccessDeniedException.class, () -> notificationService.markNotificationAsRead(notification.getId(), user2.getEmail()));
         verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllAsReadForUser_Success() {
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.markAllAsReadForRecipient(user1.getId())).thenReturn(5); // Simulate 5 notifications marked as read

        notificationService.markAllAsReadForUser(user1.getEmail());

        verify(notificationRepository).markAllAsReadForRecipient(user1.getId());
    }

     @Test
    void markAllAsReadForUser_UserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAllAsReadForUser("notfound@example.com"));
        verify(notificationRepository, never()).markAllAsReadForRecipient(anyInt());
    }
}