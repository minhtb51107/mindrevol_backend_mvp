// File: src/main/java/com/example/demo/progress/service/ProgressService.java
package com.example.demo.progress.service;

import com.example.demo.progress.dto.request.CheckInRequest;
import com.example.demo.progress.dto.response.TimelineResponse;
import com.example.demo.user.dto.response.UserStatsResponse;
import com.example.demo.progress.dto.response.ProgressChartDataResponse;

// --- CÁC IMPORT MỚI ---
import com.example.demo.progress.dto.request.UpdateCheckInRequest;
import com.example.demo.user.entity.User;
// --- IMPORT CÁC DTO TỪ PACKAGE 'community' MÀ BẠN ĐÃ CÓ ---
import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.dto.response.CommentResponse;
// --- KẾT THÚC IMPORT MỚI ---

import java.time.LocalDate;
import java.util.List;

public interface ProgressService {

    // --- CÁC HÀM GỐC (Giữ nguyên) ---
    TimelineResponse.CheckInEventResponse createCheckIn(String shareableLink, String userEmail, CheckInRequest request);
    TimelineResponse getDailyTimeline(String shareableLink, String userEmail, LocalDate date);
    TimelineResponse.CheckInEventResponse updateCheckIn(Long checkInEventId, UpdateCheckInRequest request, String userEmail);
    void deleteCheckIn(Long checkInEventId, String userEmail);

    // --- CÁC PHƯƠƠNG THỨC STATS/CHART (Giữ nguyên) ---
    UserStatsResponse getUserStats(String userEmail);
    List<ProgressChartDataResponse> getProgressChartData(String userEmail);
    

    // === THÊM CÁC HÀM MỚI CHO COMMENT VÀ REACTION ===
    
    /**
     * Thêm bình luận vào một CheckInEvent.
     */
    CommentResponse addCommentToCheckIn(Long checkInEventId, PostCommentRequest request, String userEmail);
    
    /**
     * Cập nhật một bình luận đã tồn tại.
     */
    CommentResponse updateCheckInComment(Long commentId, UpdateCommentRequest request, String userEmail);
    
    /**
     * Xóa một bình luận.
     */
    void deleteCheckInComment(Long commentId, String userEmail);
    
    /**
     * Thêm/xóa (toggle) một reaction trên một CheckInEvent.
     */
    void toggleReactionOnCheckIn(Long checkInEventId, AddReactionRequest request, String userEmail);
}