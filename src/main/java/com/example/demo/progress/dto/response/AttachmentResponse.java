package com.example.demo.progress.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class AttachmentResponse {
    private Long id; // ID của attachment
    private String originalFilename; // Tên file gốc
    private String storedFilename; // Tên file lưu trữ (để tham chiếu nếu cần xóa)
    private String fileUrl; // URL để truy cập/hiển thị file
    private String contentType; // Loại file
    private Long fileSize; // Kích thước file
    private OffsetDateTime uploadedAt; // Thời gian upload
}