package com.example.demo.progress.service;

import com.example.demo.progress.dto.request.CheckInRequest;
import com.example.demo.progress.dto.response.TimelineResponse;
import com.example.demo.user.dto.response.UserStatsResponse;
import com.example.demo.progress.dto.response.ProgressChartDataResponse;

import java.time.LocalDate;
import java.util.List;

public interface ProgressService {

    /**
     * Thực hiện một lần check-in (tạo Card Task trên Timeline).
     */
    TimelineResponse.CheckInEventResponse createCheckIn(String shareableLink, String userEmail, CheckInRequest request);

    /**
     * Lấy dữ liệu Timeline (Cột Giữa) cho một ngày cụ thể.
     */
    TimelineResponse getDailyTimeline(String shareableLink, String userEmail, LocalDate date);

    
    // --- CÁC PHƯƠNG THỨC NÀY CẦN ĐƯỢC ĐỊNH NGHĨA LẠI ---
    // Logic cũ của chúng dựa trên DailyProgress đã bị loại bỏ.
    // Tạm thời giữ lại interface, nhưng ServiceImpl sẽ (hoặc không) implement chúng.

    UserStatsResponse getUserStats(String userEmail);

    List<ProgressChartDataResponse> getProgressChartData(String userEmail);
}