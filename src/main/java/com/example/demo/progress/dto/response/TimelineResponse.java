package com.example.demo.progress.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set; // (Import này có trong file gốc của bạn, tôi giữ nguyên)

// DTO này là một List<MemberTimeline>
public class TimelineResponse extends java.util.ArrayList<TimelineResponse.MemberTimeline> {

    @Getter
    @Setter
    @Builder
    public static class MemberTimeline {
        private MemberInfo member;
        private List<CheckInEventResponse> checkIns; // Danh sách check-in của thành viên này
    }

    @Getter
    @Setter
    @Builder
    public static class MemberInfo {
        private Integer userId;
        private String userEmail;
        private String userFullName;
        // Thêm avatarUrl nếu có
    }

    @Getter
    @Setter
    @Builder
    public static class CheckInEventResponse {
        private Long id;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime checkInTimestamp; // Thời điểm check-in chính xác
        
        private String notes;
        private MemberInfo member; // Gửi lại thông tin user để dễ map
        
        @Builder.Default
        private List<AttachmentResponse> attachments = List.of();
        
        @Builder.Default
        private List<CompletedTaskInfo> completedTasks = List.of();

        // === THÊM MỚI 3 TRƯỜNG DƯỚI ĐÂY ===
        @Builder.Default
        private List<String> links = List.of(); // Danh sách links
        
        private int commentCount; // Tổng số bình luận
        
        private int reactionCount; // Tổng số cảm xúc
        
        private List<Long> completedTaskIds;
        // === KẾT THÚC THÊM MỚI ===
    }

    @Getter
    @Setter
    @Builder
    public static class AttachmentResponse {
        private String fileUrl;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
    }

    @Getter
    @Setter
    @Builder
    public static class CompletedTaskInfo {
        private Long taskId;
        private String description;
    }
}