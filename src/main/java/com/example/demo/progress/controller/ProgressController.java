package com.example.demo.progress.controller;

import com.example.demo.progress.dto.request.CheckInRequest;
import com.example.demo.progress.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/plans/{shareableLink}/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    /**
     * Endpoint mới: Thực hiện Check-in
     */
    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createCheckIn(
            @PathVariable String shareableLink,
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        // Trả về 201 CREATED
        return new ResponseEntity<>(progressService.createCheckIn(shareableLink, userEmail, request), HttpStatus.CREATED);
    }

    /**
     * Endpoint mới: Lấy dữ liệu Timeline cho một ngày
     */
    @GetMapping("/timeline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDailyTimeline(
            @PathVariable String shareableLink,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(progressService.getDailyTimeline(shareableLink, userEmail, date));
    }

    // --- CÁC ENDPOINT CŨ BỊ XÓA ---
    // (POST / và GET /dashboard đã bị thay thế)
    
    // --- CÁC ENDPOINT STATS/CHART ---
    // Vẫn giữ lại (nhưng service đã cảnh báo là logic cũ không còn hoạt động)
    // (Các endpoint này có thể nằm ở UserController hoặc 1 controller riêng thì tốt hơn)
    
    // Lưu ý: Các endpoint này không có @PathVariable shareableLink
    // Chúng ta nên di chuyển chúng ra khỏi ProgressController (vốn yêu cầu shareableLink)
    // Nhưng hiện tại, tôi sẽ giữ chúng theo cấu trúc service bạn đã cung cấp
    
    /* @GetMapping("/stats") // Giả sử một endpoint khác
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getStats(Authentication authentication) {
        return ResponseEntity.ok(progressService.getUserStats(authentication.getName()));
    }
    
    @GetMapping("/chart") // Giả sử một endpoint khác
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getChart(Authentication authentication) {
        return ResponseEntity.ok(progressService.getProgressChartData(authentication.getName()));
    }
    */
}