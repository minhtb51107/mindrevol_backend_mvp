package com.example.demo.plan.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

// DTO này chỉ chứa thông tin công khai, không lộ email thành viên
@Getter
@Setter
@Builder
public class PlanPublicResponse {
    private String title;
    private String description;
    private int durationInDays;
    private String creatorFullName; // Hiển thị tên thay vì email
    private int memberCount;
}