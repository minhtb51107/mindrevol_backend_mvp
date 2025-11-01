package com.example.demo.shared.controller;

import com.example.demo.shared.dto.response.FileUploadResponse;
import com.example.demo.shared.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList; // Thêm import
import java.util.List; // Thêm import

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * Cập nhật để xử lý nhiều file (List) với key là "files"
     * Thay đổi chữ ký phương thức và logic bên trong
     */
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FileUploadResponse>> uploadFiles(@RequestParam("files") List<MultipartFile> files) {
        
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            log.warn("Upload attempt with empty file list.");
            // Trả về một danh sách rỗng với lỗi bad request
            return ResponseEntity.badRequest().body(
                List.of(FileUploadResponse.builder().message("Files không được rỗng.").build())
            );
        }

        List<FileUploadResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
             if (file.isEmpty()) {
                 responses.add(FileUploadResponse.builder()
                                 .message("Một file bị rỗng.")
                                 .originalFilename("N/A")
                                 .build());
                 continue; // Bỏ qua file rỗng
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
                        .size(file.getSize()) // Sửa lỗi: dùng getSize()
                        .contentType(file.getContentType())
                        .originalFilename(file.getOriginalFilename())
                        .build();
                
                responses.add(response);

            } catch (IOException ex) {
                log.error("Lỗi I/O khi upload file: {}", file.getOriginalFilename(), ex);
                 responses.add(FileUploadResponse.builder()
                                 .message("Lưu file thất bại.")
                                 .originalFilename(file.getOriginalFilename())
                                 .build());
            } catch (Exception ex) {
                 log.error("Lỗi không xác định khi upload file: {}", file.getOriginalFilename(), ex);
                 responses.add(FileUploadResponse.builder()
                                 .message("Lỗi server khi upload file.")
                                 .originalFilename(file.getOriginalFilename())
                                 .build());
            }
        }
        
        // Trả về danh sách các response
        return ResponseEntity.ok(responses);
    }
}