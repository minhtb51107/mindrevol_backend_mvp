package com.example.demo.shared.service.impl;

import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.service.FileUploadService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileUploadServiceImpl implements FileUploadService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path rootLocation;

    @PostConstruct // Chạy sau khi Service được khởi tạo
    public void init() {
        try {
            rootLocation = Paths.get(uploadDir);
            Files.createDirectories(rootLocation); // Tạo thư mục nếu chưa tồn tại
            log.info("Thư mục lưu trữ file: {}", rootLocation.toAbsolutePath());
        } catch (IOException e) {
            log.error("Không thể khởi tạo thư mục lưu trữ file", e);
            throw new RuntimeException("Không thể khởi tạo thư mục lưu trữ file", e);
        }
    }

    @Override
    public String storeFile(MultipartFile file) throws IOException {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        log.info("Nhận file upload: {}", originalFilename);

        if (file.isEmpty()) {
            throw new BadRequestException("File upload không được rỗng.");
        }
        if (originalFilename.contains("..")) {
            // Security check
            throw new BadRequestException("Tên file chứa ký tự không hợp lệ: " + originalFilename);
        }

        // Tạo tên file duy nhất để tránh trùng lặp
        String fileExtension = "";
        int lastDotIndex = originalFilename.lastIndexOf(".");
        if (lastDotIndex > 0) {
            fileExtension = originalFilename.substring(lastDotIndex);
        }
        String storedFilename = UUID.randomUUID().toString() + fileExtension;

        Path destinationFile = this.rootLocation.resolve(storedFilename).normalize().toAbsolutePath();

        // Kiểm tra xem destination có nằm trong thư mục upload không (quan trọng cho bảo mật)
        if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
            throw new BadRequestException("Không thể lưu file ra ngoài thư mục quy định.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Đã lưu file thành công: {}", destinationFile);
            return storedFilename; // Trả về tên file đã lưu
        } catch (IOException e) {
            log.error("Lưu file thất bại: {}", originalFilename, e);
            throw new IOException("Lưu file thất bại: " + originalFilename, e);
        }
    }
}