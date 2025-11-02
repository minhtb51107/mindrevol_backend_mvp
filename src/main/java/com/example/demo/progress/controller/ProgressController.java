// File: src/main/java/com/example/demo/progress/controller/ProgressController.java
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
import com.example.demo.progress.dto.response.TimelineResponse;
import com.example.demo.user.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// --- IMPORT CÁC DTO TỪ PACKAGE 'community' MÀ BẠN ĐÃ CÓ ---
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.response.CommentResponse;
// --- KẾT THÚC IMPORT MỚI ---

@RestController
@RequestMapping("/api/v1/plans/{shareableLink}/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    // --- CÁC HÀM GỐC CỦA BẠN (Giữ nguyên) ---
    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TimelineResponse.CheckInEventResponse> createCheckIn(
            @PathVariable String shareableLink,
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return new ResponseEntity<>(progressService.createCheckIn(shareableLink, userEmail, request), HttpStatus.CREATED);
    }

    @GetMapping("/timeline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TimelineResponse> getDailyTimeline(
            @PathVariable String shareableLink,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(progressService.getDailyTimeline(shareableLink, userEmail, date));
    }

    @PutMapping("/check-in/{checkInEventId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TimelineResponse.CheckInEventResponse> updateCheckIn(
            @PathVariable String shareableLink, 
            @PathVariable Long checkInEventId,
            @Valid @RequestBody UpdateCheckInRequest request,
            Authentication authentication) { 
        String userEmail = authentication.getName(); 
        TimelineResponse.CheckInEventResponse updatedEvent = progressService.updateCheckIn(checkInEventId, request, userEmail);
        return ResponseEntity.ok(updatedEvent);
    }

    @DeleteMapping("/check-in/{checkInEventId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteCheckIn(
            @PathVariable String shareableLink, 
            @PathVariable Long checkInEventId,
            Authentication authentication) { 
        String userEmail = authentication.getName();
        progressService.deleteCheckIn(checkInEventId, userEmail);
        return ResponseEntity.noContent().build(); 
    }

    
    // === THÊM CÁC API MỚI CHO COMMENT VÀ REACTION ===

    /**
     * API mới: Thêm bình luận vào một CheckInEvent
     */
    @PostMapping("/check-in/{checkInEventId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponse> addCommentToCheckIn(
            @PathVariable String shareableLink, // Giữ shareableLink để khớp URL
            @PathVariable Long checkInEventId,
            @Valid @RequestBody PostCommentRequest request, // Tái sử dụng DTO từ package community
            Authentication authentication) {
        
        String userEmail = authentication.getName();
        CommentResponse newComment = progressService.addCommentToCheckIn(checkInEventId, request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(newComment);
    }

    /**
     * API mới: Cập nhật một bình luận
     */
    @PutMapping("/check-in/{checkInEventId}/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponse> updateCheckInComment(
            @PathVariable String shareableLink,
            @PathVariable Long checkInEventId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request, // Tái sử dụng DTO từ package community
            Authentication authentication) {
                
        String userEmail = authentication.getName();
        CommentResponse updatedComment = progressService.updateCheckInComment(commentId, request, userEmail);
        return ResponseEntity.ok(updatedComment);
    }

    /**
     * API mới: Xóa một bình luận
     */
    @DeleteMapping("/check-in/{checkInEventId}/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteCheckInComment(
            @PathVariable String shareableLink,
            @PathVariable Long checkInEventId,
            @PathVariable Long commentId,
            Authentication authentication) {
                
        String userEmail = authentication.getName();
        progressService.deleteCheckInComment(commentId, userEmail);
        return ResponseEntity.noContent().build();
    }

    /**
     * API mới: Thêm/xóa (toggle) reaction
     */
    @PostMapping("/check-in/{checkInEventId}/reactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> toggleReactionOnCheckIn(
            @PathVariable String shareableLink,
            @PathVariable Long checkInEventId,
            @Valid @RequestBody AddReactionRequest request, // Tái sử dụng DTO từ package community
            Authentication authentication) {
                
        String userEmail = authentication.getName();
        progressService.toggleReactionOnCheckIn(checkInEventId, request, userEmail);
        return ResponseEntity.ok().build(); // Trả về 200 OK (hoặc 201/204 tùy logic)
    }

    // --- CÁC ENDPOINT STATS/CHART CŨ (giữ nguyên) ---
}