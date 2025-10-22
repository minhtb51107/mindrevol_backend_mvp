package com.example.demo.shared.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface FileUploadService {
    /**
     * Lưu file upload và trả về đường dẫn hoặc thông tin cần thiết.
     * @param file Đối tượng MultipartFile từ request.
     * @return Thông tin về file đã lưu (ví dụ: đường dẫn tương đối, tên file lưu trữ).
     * @throws IOException Nếu có lỗi khi lưu file.
     */
    String storeFile(MultipartFile file) throws IOException; // Trả về storedFilename

    // Có thể thêm các phương thức khác như deleteFile, loadFileAsResource,...
}