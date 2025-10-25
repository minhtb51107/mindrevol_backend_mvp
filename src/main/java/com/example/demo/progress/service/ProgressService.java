package com.example.demo.progress.service;

import com.example.demo.progress.dto.request.LogProgressRequest;
import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.dto.response.ProgressDashboardResponse;
import com.example.demo.user.dto.response.UserStatsResponse;
import com.example.demo.progress.dto.response.ProgressChartDataResponse; // *** THÊM IMPORT ***
import java.util.List; // *** THÊM IMPORT ***


public interface ProgressService {

    DailyProgressResponse logOrUpdateDailyProgress(String shareableLink, String userEmail, LogProgressRequest request);

    ProgressDashboardResponse getProgressDashboard(String shareableLink, String userEmail);

    UserStatsResponse getUserStats(String userEmail);

    // *** THÊM PHƯƠNG THỨC MỚI ***
    /**
     * Lấy dữ liệu tiến độ cho biểu đồ 7 ngày gần nhất của người dùng.
     * @param userEmail Email của người dùng.
     * @return Danh sách dữ liệu cho biểu đồ.
     */
    List<ProgressChartDataResponse> getProgressChartData(String userEmail);
}