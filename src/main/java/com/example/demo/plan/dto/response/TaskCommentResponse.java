package com.example.demo.plan.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class TaskCommentResponse {
    private Long id;
    private String content;
    private String authorEmail;
    private String authorFullName;
    private OffsetDateTime createdAt;
    // --- THÊM TRƯỜNG NÀY ---
    private OffsetDateTime updatedAt; // Có thể null nếu chưa update
    // --- KẾT THÚC THÊM ---
    private Long taskId;
    private Integer authorId;
}