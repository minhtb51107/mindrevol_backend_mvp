package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserStatsResponse {
    private boolean checkedInTodayComplete; // Đổi tên: True nếu đã check-in VÀ hoàn thành TẤT CẢ
    private int currentStreak;
    private int totalTasksToday;        // Tổng số task của các plan active hôm nay
    private int completedTasksToday;    // Số task đã hoàn thành hôm nay
}