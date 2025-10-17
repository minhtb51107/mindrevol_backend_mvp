package com.example.demo.user.controller;

import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto.response.UserActivityLogResponse;
import com.example.demo.user.service.UserActivityLogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/activity-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Chỉ Admin mới được xem log
public class UserActivityLogController {

    private final UserActivityLogService userActivityLogService;

    /**
     * API để lấy và lọc lịch sử hoạt động.
     * Ví dụ: /api/v1/activity-logs?userId=1&page=0&size=20
     * /api/v1/activity-logs?action=LOGIN&startDate=2025-10-10T00:00:00Z
     */
 // Bên trong lớp UserActivityLogController

 // minhtb51107/shop_backend/shop_backend-integration/src/main/java/com/example/demo/user/controller/UserActivityLogController.java

    @GetMapping
    public ResponseEntity<Page<UserActivityLogResponse>> searchActivityLogs(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            Pageable pageable) { // Sửa ở đây

        Page<UserActivityLogResponse> logs = userActivityLogService.searchLogs(userId, action, startDate, endDate, pageable);

        return ResponseEntity.ok(logs);
    }
}
