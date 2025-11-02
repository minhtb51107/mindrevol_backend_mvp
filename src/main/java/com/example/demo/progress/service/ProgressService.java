package com.example.demo.progress.service;

import com.example.demo.progress.dto.request.CheckInRequest;
import com.example.demo.progress.dto.response.TimelineResponse;
import com.example.demo.user.dto.response.UserStatsResponse;
import com.example.demo.progress.dto.response.ProgressChartDataResponse;

// --- CÁC IMPORT MỚI ---
import com.example.demo.progress.dto.request.UpdateCheckInRequest;
import com.example.demo.user.entity.User; // (Sử dụng User object sẽ tốt hơn)
// --- KẾT THÚC IMPORT MỚI ---

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
    UserStatsResponse getUserStats(String userEmail);

    List<ProgressChartDataResponse> getProgressChartData(String userEmail);
    
    // --- (MỚI) HÀM SỬA VÀ XÓA CHECK-IN ---
    
    /**
     * Cập nhật một CheckInEvent đã tồn tại.
     * @param checkInEventId ID của check-in
     * @param request DTO chứa thông tin cập nhật
     * @param userEmail Email của user đang thực hiện (để xác thực)
     * @return CheckInEvent đã được cập nhật
     */
    TimelineResponse.CheckInEventResponse updateCheckIn(Long checkInEventId, UpdateCheckInRequest request, String userEmail);

    /**
     * Xóa một CheckInEvent.
     * @param checkInEventId ID của check-in
     * @param userEmail Email của user đang thực hiện (để xác thực)
     */
    void deleteCheckIn(Long checkInEventId, String userEmail);
}