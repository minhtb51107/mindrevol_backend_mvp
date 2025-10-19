package com.example.demo.progress.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set; // Thêm import
import java.util.Collections; // Thêm import


@Getter
@Setter
@Builder
public class DailyProgressSummaryResponse {
    private Long id;
    private boolean completed;
    private String notes;
    private String evidence;
    @Builder.Default
    private List<DailyProgressResponse.CommentResponse> comments = Collections.emptyList(); // Khởi tạo
    @Builder.Default
    private List<DailyProgressResponse.ReactionSummaryResponse> reactions = Collections.emptyList(); // Khởi tạo
    @Builder.Default
    private Set<Integer> completedTaskIndices = Collections.emptySet(); // Thêm và khởi tạo
}