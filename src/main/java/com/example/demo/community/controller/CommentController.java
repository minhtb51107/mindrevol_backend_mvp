package com.example.demo.community.controller;

import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CommentController {

    private final CommunityService communityService;

    @PutMapping("/{commentId}")
    public ResponseEntity<?> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            Authentication authentication) {
        var updatedComment = communityService.updateComment(commentId, authentication.getName(), request);
        return ResponseEntity.ok(updatedComment);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long commentId,
            Authentication authentication) {
        communityService.deleteComment(commentId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}