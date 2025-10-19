package com.example.demo.progress.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Set; // Thêm import
import java.util.Collections; // Thêm import

@Getter
@Setter
@Builder
public class DailyProgressResponse {
    private Long id;
    private LocalDate date;
    private boolean completed;
    private String notes;
    private String evidence;
    @Builder.Default
    private List<CommentResponse> comments = Collections.emptyList(); // Khởi tạo default
    @Builder.Default
    private List<ReactionSummaryResponse> reactions = Collections.emptyList(); // Khởi tạo default
    @Builder.Default
    private Set<Integer> completedTaskIndices = Collections.emptySet(); // Thêm trường này và khởi tạo

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
        private boolean hasCurrentUserReacted;
    }
}