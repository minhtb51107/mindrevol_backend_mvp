package com.example.demo.community.controller;

import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/progress/{progressId}")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @PostMapping("/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> postComment(
            @PathVariable Long progressId,
            @Valid @RequestBody PostCommentRequest request,
            Authentication authentication) {
        var comment = communityService.postComment(progressId, authentication.getName(), request);
        return new ResponseEntity<>(comment, HttpStatus.CREATED);
    }
    
    @PostMapping("/reactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> addReaction(
            @PathVariable Long progressId,
            @Valid @RequestBody AddReactionRequest request,
            Authentication authentication) {
        communityService.addOrUpdateReaction(progressId, authentication.getName(), request);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/reactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeReaction(
            @PathVariable Long progressId,
            Authentication authentication) {
        communityService.removeReaction(progressId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}