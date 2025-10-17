package com.example.demo.progress.controller;

import com.example.demo.progress.dto.request.LogProgressRequest;
import com.example.demo.progress.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plans/{shareableLink}/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> logProgress(
            @PathVariable String shareableLink,
            @Valid @RequestBody LogProgressRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(progressService.logOrUpdateDailyProgress(shareableLink, userEmail, request));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDashboard(
            @PathVariable String shareableLink,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(progressService.getProgressDashboard(shareableLink, userEmail));
    }
}