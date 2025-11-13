package com.example.demo.progress.dto.response;

import com.example.demo.community.dto.response.CommentResponse;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogResponseDto {
    private Long id;
    private String notes;
    private LocalDateTime checkInTimestamp; // SỬA LỖI 2: Chỉ giữ lại timestamp này
    // private LocalDateTime createdAt; // XÓA
    // private LocalDateTime updatedAt; // XÓA
    private List<AttachmentResponse> attachments; // SỬA LỖI 4: Đây là kiểu AttachmentResponse riêng
    private List<String> links;
    
    private AuthorDto author;

    // SỬA LỖI 1: Dùng CompletedTaskInfo (từ TimelineResponse)
    private List<TimelineResponse.CompletedTaskInfo> completedTasks;

    // SỬA LỖI 1: Dùng ReactionResponse (từ TimelineResponse)
    private List<TimelineResponse.ReactionResponse> reactions;

    private List<CommentResponse> comments;
    private int commentCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorDto {
        private Long userId;
        private String userFullName;
        private String userAvatar;
    }
}