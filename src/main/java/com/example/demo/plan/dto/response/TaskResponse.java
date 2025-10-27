package com.example.demo.plan.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate; // --- THÊM IMPORT ---
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;

@Getter
@Setter
@Builder
public class TaskResponse {
    private Long id;
    private String description;
    private Integer order;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime deadlineTime;

    // --- THÊM TRƯỜNG NÀY ---
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate taskDate;
    // --- KẾT THÚC THÊM ---

    @Builder.Default
    private List<TaskCommentResponse> comments = new ArrayList<>();

    @Builder.Default
    private List<TaskAttachmentResponse> attachments = new ArrayList<>();
}