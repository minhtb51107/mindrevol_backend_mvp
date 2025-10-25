// src/test/java/com/example/demo/notification/service/impl/NotificationServiceImplTestNG.java
package com.example.demo.notification.service.impl;

// TestNG imports
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
// Mockito TestNG Listener
import org.mockito.testng.MockitoTestNGListener;

// Other imports from JUnit version
import com.example.demo.notification.dto.response.NotificationResponse;
import com.example.demo.notification.entity.Notification;
import com.example.demo.notification.mapper.NotificationMapper;
import com.example.demo.notification.repository.NotificationRepository;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@Listeners(MockitoTestNGListener.class)
public class NotificationServiceImplTestNG {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Spy
    private NotificationMapper notificationMapper = new NotificationMapper();

    @InjectMocks
    private NotificationServiceImpl notificationService;

    // --- Variables giữ nguyên ---
    private User user1;
    private User user2;
    private Notification notification;

    @BeforeMethod // Thay @BeforeEach
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
        Assert.assertEquals(savedNotification.getRecipient(), user1); // Thay assertion
        Assert.assertEquals(savedNotification.getMessage(), "New message");
        Assert.assertEquals(savedNotification.getLink(), "/link");
        Assert.assertFalse(savedNotification.isRead());
    }

    @Test
    void getNotificationsForUser_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notificationPage = new PageImpl<>(Collections.singletonList(notification), pageable, 1);

        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user1.getId(), pageable)).thenReturn(notificationPage);

        Page<NotificationResponse> result = notificationService.getNotificationsForUser(user1.getEmail(), pageable);

        Assert.assertNotNull(result); // Thay assertion
        Assert.assertEquals(result.getTotalElements(), 1);
        Assert.assertEquals(result.getContent().get(0).getMessage(), notification.getMessage());
        verify(notificationMapper).toNotificationResponse(notification);
    }

     @Test(expectedExceptions = ResourceNotFoundException.class)
    void getNotificationsForUser_UserNotFound() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        try {
            notificationService.getNotificationsForUser("notfound@example.com", pageable);
        } finally {
            verify(notificationRepository, never()).findByRecipientIdOrderByCreatedAtDesc(anyInt(), any());
        }
    }

    @Test
    void markNotificationAsRead_Success() {
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        Assert.assertFalse(notification.isRead()); // Thay assertion
        notificationService.markNotificationAsRead(notification.getId(), user1.getEmail());

        Assert.assertTrue(notification.isRead()); // Thay assertion
        verify(notificationRepository).save(notification);
    }

     @Test
    void markNotificationAsRead_AlreadyRead() {
        notification.setRead(true);
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        notificationService.markNotificationAsRead(notification.getId(), user1.getEmail());

        Assert.assertTrue(notification.isRead()); // Thay assertion
        verify(notificationRepository, never()).save(notification);
    }

    @Test(expectedExceptions = ResourceNotFoundException.class)
    void markNotificationAsRead_NotificationNotFound() {
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        try {
            notificationService.markNotificationAsRead(99L, user1.getEmail());
        } finally {
             verify(notificationRepository, never()).save(any());
        }
    }

    @Test(expectedExceptions = AccessDeniedException.class)
    void markNotificationAsRead_AccessDenied() {
        when(userRepository.findByEmail(user2.getEmail())).thenReturn(Optional.of(user2));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        try {
            notificationService.markNotificationAsRead(notification.getId(), user2.getEmail());
        } finally {
             verify(notificationRepository, never()).save(any());
        }
    }

    @Test
    void markAllAsReadForUser_Success() {
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(notificationRepository.markAllAsReadForRecipient(user1.getId())).thenReturn(5);

        notificationService.markAllAsReadForUser(user1.getEmail());

        verify(notificationRepository).markAllAsReadForRecipient(user1.getId());
    }

     @Test(expectedExceptions = ResourceNotFoundException.class)
    void markAllAsReadForUser_UserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        try {
            notificationService.markAllAsReadForUser("notfound@example.com");
        } finally {
            verify(notificationRepository, never()).markAllAsReadForRecipient(anyInt());
        }
    }
}