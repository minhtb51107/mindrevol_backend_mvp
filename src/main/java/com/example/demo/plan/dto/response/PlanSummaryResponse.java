package com.example.demo.plan.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class PlanSummaryResponse {
    private Integer id;
    private String title;
    private String description;
    private int durationInDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private String displayStatus; // Trạng thái hiển thị (ACTIVE, COMPLETED...)
    private String shareableLink;
    private int memberCount;
    private String role; // Vai trò của người dùng hiện tại trong plan (OWNER/MEMBER)
}