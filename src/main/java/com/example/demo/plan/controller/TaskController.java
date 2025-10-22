package com.example.demo.plan.controller;

import com.example.demo.plan.dto.request.TaskCommentRequest;
import com.example.demo.plan.dto.request.UpdateTaskCommentRequest; // Thêm import
import com.example.demo.plan.service.TaskService;
import com.example.demo.shared.dto.response.FileUploadResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TaskController {

    private final TaskService taskService;

    // --- Endpoints cho Comments ---

    @PostMapping("/{taskId}/comments")
    public ResponseEntity<?> addTaskComment(
            @PathVariable Long taskId,
            @Valid @RequestBody TaskCommentRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        var commentResponse = taskService.addTaskComment(taskId, request, userEmail);
        return new ResponseEntity<>(commentResponse, HttpStatus.CREATED);
    }

    // --- THÊM ENDPOINT NÀY ---
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<?> updateTaskComment(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateTaskCommentRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        var updatedComment = taskService.updateTaskComment(commentId, request, userEmail);
        return ResponseEntity.ok(updatedComment);
    }
    // --- KẾT THÚC THÊM ---

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteTaskComment(
            @PathVariable Long commentId,
            Authentication authentication) {
        String userEmail = authentication.getName();
        taskService.deleteTaskComment(commentId, userEmail);
        return ResponseEntity.noContent().build();
    }


    // --- Endpoints cho Attachments (giữ nguyên) ---

    @PostMapping("/{taskId}/attachments")
    public ResponseEntity<?> addTaskAttachment(
            @PathVariable Long taskId,
            @Valid @RequestBody FileUploadResponse fileInfo,
            Authentication authentication) {
        String userEmail = authentication.getName();
        var attachmentResponse = taskService.addTaskAttachment(taskId, fileInfo, userEmail);
        return new ResponseEntity<>(attachmentResponse, HttpStatus.CREATED);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteTaskAttachment(
            @PathVariable Long attachmentId,
            Authentication authentication) {
        String userEmail = authentication.getName();
        taskService.deleteTaskAttachment(attachmentId, userEmail);
        return ResponseEntity.noContent().build();
    }
}