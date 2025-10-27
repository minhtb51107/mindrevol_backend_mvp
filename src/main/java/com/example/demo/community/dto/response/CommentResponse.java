package com.example.demo.community.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime; // Thêm nếu bạn có trường createdAt

@Getter
@Setter
@Builder
public class CommentResponse {
    private Long id;
    private String content;
    private String authorEmail;
    private String authorFullName;
    // private LocalDateTime createdAt; // Thêm nếu cần
}