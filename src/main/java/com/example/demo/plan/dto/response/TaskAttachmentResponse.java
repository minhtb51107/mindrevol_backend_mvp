package com.example.demo.plan.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class TaskAttachmentResponse {
    private Long id; // ID của attachment
    private String originalFilename; // Tên file gốc
    private String storedFilename; // Tên file lưu trữ
    private String fileUrl; // URL để truy cập/hiển thị file
    private String contentType; // Loại file
    private Long fileSize; // Kích thước file
    private OffsetDateTime uploadedAt; // Thời gian upload
    private Long taskId; // ID của Task chứa attachment này
    // Không cần authorId vì logic xóa file có thể khác comment
}