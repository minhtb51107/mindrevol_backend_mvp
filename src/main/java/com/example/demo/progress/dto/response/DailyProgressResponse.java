package com.example.demo.progress.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
public class DailyProgressResponse {
    private Long id;
    private LocalDate date;
    private boolean completed;
    private String notes;
    private String evidence;
    private List<CommentResponse> comments;
    private List<ReactionSummaryResponse> reactions;

    @Getter
    @Setter
    @Builder
    public static class CommentResponse {
        private Long id;
        private String content;
        private String authorEmail;
        private String authorFullName;
    }

    @Getter @Setter @Builder
    public static class ReactionSummaryResponse {
        private String type;
        private int count;
        private boolean hasCurrentUserReacted; // Thay thế cho List<String> userEmails
    }
}