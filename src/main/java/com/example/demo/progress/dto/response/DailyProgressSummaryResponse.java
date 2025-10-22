package com.example.demo.progress.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;

@Getter
@Setter
@Builder
public class DailyProgressSummaryResponse {
    private Long id;
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
    private List<DailyProgressResponse.CommentResponse> comments = Collections.emptyList();
    @Builder.Default
    private List<DailyProgressResponse.ReactionSummaryResponse> reactions = Collections.emptyList();
    @Builder.Default
    private Set<Long> completedTaskIds = new HashSet<>();
}