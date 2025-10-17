package com.example.demo.notification.controller;

import com.example.demo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getNotifications(Authentication authentication, Pageable pageable) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(notificationService.getNotificationsForUser(userEmail, pageable));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {
        String userEmail = authentication.getName();
        notificationService.markNotificationAsRead(notificationId, userEmail);
        return ResponseEntity.ok().build();
    }
}