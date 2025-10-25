package com.example.demo.feed.controller;

import com.example.demo.feed.dto.FeedEventDto;
import com.example.demo.feed.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feed") // Endpoint chung cho feed
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @GetMapping
    @PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập để xem feed
    public ResponseEntity<Page<FeedEventDto>> getMyFeed(
            Authentication authentication,
            Pageable pageable) { // Spring tự động inject Pageable từ query params (page, size, sort)

        String userEmail = authentication.getName();
        Page<FeedEventDto> feedPage = feedService.getRecentFeedForUser(userEmail, pageable);
        return ResponseEntity.ok(feedPage);
    }
}