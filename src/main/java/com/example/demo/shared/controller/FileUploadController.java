package com.example.demo.shared.controller;

import com.example.demo.shared.dto.response.FileUploadResponse;
import com.example.demo.shared.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    // Không cần inject uploadDir ở đây nếu không dùng trực tiếp
    // @Value("${file.upload-dir}")
    // private String uploadDir;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        // Sử dụng một DTO khác cho response lỗi để tránh nhầm lẫn
        // Hoặc có thể dùng ResponseEntity<String> hoặc một cấu trúc lỗi chuẩn
        if (file.isEmpty()) {
            log.warn("Upload attempt with empty file.");
            // Trả về lỗi 400 Bad Request rõ ràng hơn
            return ResponseEntity.badRequest().body(
                FileUploadResponse.builder().message("File không được rỗng.").build()
            );
        }

        try {
            String storedFilename = fileUploadService.storeFile(file);

            String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/") // Đảm bảo khớp với WebConfig
                    .path(storedFilename)
                    .toUriString();

            log.info("Upload thành công file: {}, Original: {}, URL: {}",
                     storedFilename, file.getOriginalFilename(), fileDownloadUri);

            FileUploadResponse response = FileUploadResponse.builder()
                    .storedFilename(storedFilename)
                    .fileUrl(fileDownloadUri)
                    .message("Upload file thành công!")
                    .size(file.getSize())
                    .contentType(file.getContentType())
                    .originalFilename(file.getOriginalFilename()) // *** Đảm bảo gán giá trị ở đây ***
                    .build();

            return ResponseEntity.ok(response);

        } catch (IOException ex) {
            log.error("Lỗi I/O khi upload file: {}", file.getOriginalFilename(), ex);
             return ResponseEntity.status(500).body(
                FileUploadResponse.builder().message("Lưu file thất bại.").originalFilename(file.getOriginalFilename()).build()
             );
        } catch (Exception ex) {
             log.error("Lỗi không xác định khi upload file: {}", file.getOriginalFilename(), ex);
             return ResponseEntity.status(500).body(
                FileUploadResponse.builder().message("Lỗi server khi upload file.").originalFilename(file.getOriginalFilename()).build()
             );
        }
    }
}