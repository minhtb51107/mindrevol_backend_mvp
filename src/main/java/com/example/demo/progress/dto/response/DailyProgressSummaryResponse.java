// File: src/main/java/com/example/demo/progress/dto/response/DailyProgressSummaryResponse.java

package com.example.demo.progress.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

// DTO này chứa thông tin tóm tắt cần thiết cho ô tiến độ trên dashboard
@Getter
@Setter
@Builder
public class DailyProgressSummaryResponse {
    private Long id;
    private boolean completed;
    private String notes;
    private String evidence;
    private List<DailyProgressResponse.CommentResponse> comments;
    private List<DailyProgressResponse.ReactionSummaryResponse> reactions;
}