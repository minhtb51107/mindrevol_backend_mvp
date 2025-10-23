package com.example.demo.plan.dto.response;

import com.example.demo.plan.entity.PlanStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;

@Getter
@Setter
@Builder
public class PlanDetailResponse {
    private Integer id;
    private String title;
    private String description;
    private int durationInDays;
    private String dailyGoal;
    private String shareableLink;
    private PlanStatus status;
    private String displayStatus;
    private LocalDate startDate;
    private LocalDate endDate;
    private OffsetDateTime createdAt;
    private List<PlanMemberResponse> members;

    // --- THAY ĐỔI Ở ĐÂY ---
    // Thay List<String> bằng List<TaskResponse>
    @Builder.Default
    private List<TaskResponse> dailyTasks = new ArrayList<>();
    // --- KẾT THÚC THAY ĐỔI ---

    @Getter
    @Setter
    @Builder
    public static class PlanMemberResponse {
    	private Integer userId; // *** THÊM TRƯỜNG NÀY ***
        private String userEmail;
        private String userFullName;
        private String role;
    }
}