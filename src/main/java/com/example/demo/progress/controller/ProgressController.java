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

// --- CÁC IMPORT MỚI ---
import com.example.demo.progress.dto.request.UpdateCheckInRequest;
import com.example.demo.progress.dto.response.TimelineResponse; // (Để định kiểu trả về)
import com.example.demo.user.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
// --- KẾT THÚC IMPORT MỚI ---

@RestController
@RequestMapping("/api/v1/plans/{shareableLink}/progress") // (Đường dẫn gốc của bạn)
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    /**
     * Endpoint mới: Thực hiện Check-in
     */
    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TimelineResponse.CheckInEventResponse> createCheckIn( // (Sửa kiểu trả về cho rõ ràng)
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
    public ResponseEntity<TimelineResponse> getDailyTimeline( // (Sửa kiểu trả về cho rõ ràng)
            @PathVariable String shareableLink,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(progressService.getDailyTimeline(shareableLink, userEmail, date));
    }

    // --- (MỚI) ENDPOINT SỬA CHECK-IN ---
    @PutMapping("/check-in/{checkInEventId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TimelineResponse.CheckInEventResponse> updateCheckIn(
            @PathVariable String shareableLink, // (Không dùng, nhưng có trong path)
            @PathVariable Long checkInEventId,
            @Valid @RequestBody UpdateCheckInRequest request,
            @AuthenticationPrincipal User currentUser) { // (Dùng User object)
        
        TimelineResponse.CheckInEventResponse updatedEvent = progressService.updateCheckIn(checkInEventId, request, currentUser.getEmail());
        return ResponseEntity.ok(updatedEvent);
    }

    // --- (MỚI) ENDPOINT XÓA CHECK-IN ---
    @DeleteMapping("/check-in/{checkInEventId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteCheckIn(
            @PathVariable String shareableLink, // (Không dùng, nhưng có trong path)
            @PathVariable Long checkInEventId,
            @AuthenticationPrincipal User currentUser) { // (Dùng User object)
        
        progressService.deleteCheckIn(checkInEventId, currentUser.getEmail());
        return ResponseEntity.noContent().build(); // 204 No Content
    }


    // --- CÁC ENDPOINT CŨ BỊ XÓA ---
    // (POST / và GET /dashboard đã bị thay thế)
    
    // --- CÁC ENDPOINT STATS/CHART ---
    // (Phần này giữ nguyên như file gốc của bạn)
    
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