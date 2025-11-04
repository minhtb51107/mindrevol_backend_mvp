package com.example.demo.shared.exception; // Hoặc package config của bạn

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bắt các lỗi 404
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return Map.of("error", "Not Found", "message", ex.getMessage());
    }

    // Bắt các lỗi 400
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return Map.of("error", "Bad Request", "message", ex.getMessage());
    }

    // Bắt TẤT CẢ các lỗi 500 khác (bao gồm cả TransientObjectException)
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleInternalServerError(Exception ex) {
        // Log lỗi nghiêm trọng này
        log.error("Unhandled Internal Server Error: ", ex);

        // Chỉ trả về một thông báo chung cho người dùng
        return Map.of("error", "Internal Server Error",
                      "message", "Đã xảy ra lỗi không mong muốn trên máy chủ.");
    }
}