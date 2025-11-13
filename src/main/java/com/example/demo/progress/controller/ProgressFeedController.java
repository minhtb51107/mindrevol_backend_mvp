package com.example.demo.progress.controller;

import com.example.demo.progress.dto.response.LogResponseDto;
import com.example.demo.progress.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/progress/feed") // Đặt prefix chung cho các feed global
@RequiredArgsConstructor
public class ProgressFeedController {

    private final ProgressService progressService; // Vẫn dùng ProgressService

    // Dán phương thức getFriendFeed vào đây
    @GetMapping("/friends")
    public ResponseEntity<Page<LogResponseDto>> getFriendFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("checkInTimestamp").descending());
        Page<LogResponseDto> feedPage = progressService.getFriendFeed(pageable);
        return ResponseEntity.ok(feedPage);
    }

    // Dán phương thức getUserFeed vào đây
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<LogResponseDto>> getUserFeed(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("checkInTimestamp").descending());
        Page<LogResponseDto> feedPage = progressService.getUserFeed(userId, pageable);
        return ResponseEntity.ok(feedPage);
    }
}