package com.example.demo.progress.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;

@Getter
@Setter
@Builder
public class DailyProgressResponse {
    private Long id;
    private LocalDate date;
    private boolean completed;
    private String notes;

    // --- BỎ PHẦN NÀY ---
    // @Builder.Default
    // private List<String> evidence = new ArrayList<>();
    // --- KẾT THÚC BỎ ---

    // --- THÊM PHẦN NÀY ---
    @Builder.Default
    private List<AttachmentResponse> attachments = new ArrayList<>(); // Danh sách file đính kèm
    // --- KẾT THÚC THÊM ---

    @Builder.Default
    private List<CommentResponse> comments = Collections.emptyList();
    @Builder.Default
    private List<ReactionSummaryResponse> reactions = Collections.emptyList();
    @Builder.Default
    private Set<Long> completedTaskIds = new HashSet<>();

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