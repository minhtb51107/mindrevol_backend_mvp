package com.example.demo.plan.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalTime;
import java.util.List; // Thêm import
import java.util.ArrayList; // Thêm import

@Getter
@Setter
@Builder
public class TaskResponse {
    private Long id;
    private String description;
    private Integer order;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime deadlineTime;

    // --- THÊM CÁC TRƯỜNG NÀY ---
    @Builder.Default // Khởi tạo list rỗng
    private List<TaskCommentResponse> comments = new ArrayList<>();

    @Builder.Default // Khởi tạo list rỗng
    private List<TaskAttachmentResponse> attachments = new ArrayList<>();
    // --- KẾT THÚC THÊM ---
}