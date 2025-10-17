package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class UserActivityLogResponse {
    private Long id;
    private String action;
    private String details;
    private OffsetDateTime createdAt;
    private String userEmail; // Chỉ hiển thị email, không lộ thông tin khác
}