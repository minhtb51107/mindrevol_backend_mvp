package com.example.demo.progress.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class ProgressDashboardResponse {
    private String planTitle;
    private List<MemberProgressResponse> membersProgress;

    @Getter
    @Setter
    @Builder
    public static class MemberProgressResponse {
        private String userEmail;
        private String userFullName;
        private int completedDays;
        private double completionPercentage;
        // THAY ĐỔI DÒNG DƯỚI ĐÂY
        private Map<String, DailyProgressSummaryResponse> dailyStatus; // <-- TỪ Boolean SANG DailyProgressSummaryResponse
    }
}