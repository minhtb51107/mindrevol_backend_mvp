package com.example.demo.plan.service;

import com.example.demo.plan.dto.request.TaskCommentRequest;
import com.example.demo.plan.dto.request.UpdateTaskCommentRequest; // Thêm import
import com.example.demo.plan.dto.response.TaskAttachmentResponse;
import com.example.demo.plan.dto.response.TaskCommentResponse;
import com.example.demo.shared.dto.response.FileUploadResponse;

public interface TaskService {

    TaskCommentResponse addTaskComment(Long taskId, TaskCommentRequest request, String userEmail);

    // --- THÊM PHƯƠNG THỨC NÀY ---
    /**
     * Cập nhật nội dung một bình luận Task.
     * @param commentId ID của bình luận cần cập nhật.
     * @param request DTO chứa nội dung mới.
     * @param userEmail Email của người dùng thực hiện (để kiểm tra quyền tác giả).
     * @return DTO của bình luận sau khi cập nhật.
     */
    TaskCommentResponse updateTaskComment(Long commentId, UpdateTaskCommentRequest request, String userEmail);
    // --- KẾT THÚC THÊM ---

    void deleteTaskComment(Long commentId, String userEmail);

    TaskAttachmentResponse addTaskAttachment(Long taskId, FileUploadResponse fileInfo, String userEmail);

    void deleteTaskAttachment(Long attachmentId, String userEmail);

}