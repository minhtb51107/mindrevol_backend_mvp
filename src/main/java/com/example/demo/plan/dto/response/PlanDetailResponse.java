package com.example.demo.plan.dto.response;

import com.example.demo.plan.entity.PlanStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

// DTO này chứa đầy đủ thông tin cho những người đã là thành viên
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
    private PlanStatus status; // Trạng thái thực tế trong DB
    private String displayStatus; // Trạng thái tính toán để hiển thị
    private LocalDate startDate;
    private LocalDate endDate;
    private OffsetDateTime createdAt;
    private List<PlanMemberResponse> members;

    @Getter
    @Setter
    @Builder
    public static class PlanMemberResponse {
        private String userEmail;
        private String userFullName;
        private String role;
    }
}