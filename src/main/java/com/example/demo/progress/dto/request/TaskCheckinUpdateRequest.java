package com.example.demo.progress.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.ArrayList;

@Getter
@Setter
public class TaskCheckinUpdateRequest {

    @NotNull(message = "Task ID không được thiếu")
    private Long taskId; // ID của Task được cập nhật

    // Không cần gửi trạng thái completed ở đây vì đã có completedTaskIds chung

    private String commentContent; // Nội dung bình luận mới cho Task này (optional)

    @Valid // Validate các AttachmentRequest bên trong
    private List<LogProgressRequest.AttachmentRequest> attachments = new ArrayList<>(); // Danh sách file mới đính kèm cho Task này (optional)

}