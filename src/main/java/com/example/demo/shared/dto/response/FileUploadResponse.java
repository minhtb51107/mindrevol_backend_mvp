package com.example.demo.shared.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private String storedFilename; // Tên file lưu trên server
    private String fileUrl; // URL để truy cập file
    private String message;
    private long size;
    private String contentType;

    // --- THÊM TRƯỜNG NÀY ---
    private String originalFilename; // Tên file gốc mà người dùng upload
    // --- KẾT THÚC THÊM ---
}