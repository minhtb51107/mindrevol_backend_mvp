package com.example.demo.plan.mapper;

import com.example.demo.plan.dto.response.TaskAttachmentResponse;
import com.example.demo.plan.dto.response.TaskCommentResponse;
import com.example.demo.plan.dto.response.TaskResponse;
import com.example.demo.plan.entity.Task;
import com.example.demo.plan.entity.TaskAttachment;
import com.example.demo.plan.entity.TaskComment;
import com.example.demo.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Collectors;

@Component // Đánh dấu là Spring Bean
public class TaskMapper {

    /**
     * Chuyển đổi Task entity sang TaskResponse DTO.
     * Bao gồm cả việc map danh sách comments và attachments.
     */
    public TaskResponse toTaskResponse(Task task) {
        if (task == null) {
            return null;
        }

        return TaskResponse.builder()
                .id(task.getId())
                .description(task.getDescription())
                .order(task.getOrder())
                .deadlineTime(task.getDeadlineTime())
                .comments(task.getComments() == null ? Collections.emptyList() :
                          task.getComments().stream()
                              .map(this::toTaskCommentResponse) // Sử dụng hàm map comment
                              .collect(Collectors.toList()))
                .attachments(task.getAttachments() == null ? Collections.emptyList() :
                             task.getAttachments().stream()
                                 .map(this::toTaskAttachmentResponse) // Sử dụng hàm map attachment
                                 .collect(Collectors.toList()))
                .build();
    }

    /**
     * Chuyển đổi TaskComment entity sang TaskCommentResponse DTO.
     */
    public TaskCommentResponse toTaskCommentResponse(TaskComment comment) {
        if (comment == null) {
            return null;
        }
        User author = comment.getAuthor();
        return TaskCommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .authorEmail(author != null ? author.getEmail() : "N/A")
                .authorFullName(author != null ? getUserFullName(author) : "Người dùng ẩn danh")
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt()) // Thêm mapping updatedAt
                .taskId(comment.getTask() != null ? comment.getTask().getId() : null)
                .authorId(author != null ? author.getId() : null)
                .build();
    }

    /**
     * Chuyển đổi TaskAttachment entity sang TaskAttachmentResponse DTO.
     */
    public TaskAttachmentResponse toTaskAttachmentResponse(TaskAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        return TaskAttachmentResponse.builder()
                .id(attachment.getId())
                .originalFilename(attachment.getOriginalFilename())
                .storedFilename(attachment.getStoredFilename())
                .fileUrl(attachment.getFileUrl())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .uploadedAt(attachment.getUploadedAt())
                .taskId(attachment.getTask() != null ? attachment.getTask().getId() : null) // Lấy ID của Task
                .build();
    }

    // --- HELPER LẤY TÊN ĐẦY ĐỦ (Giống trong các Mapper khác) ---
    private String getUserFullName(User user) {
        if (user == null) return "N/A";
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null && !user.getEmployee().getFullname().isBlank()) {
            return user.getEmployee().getFullname();
        }
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null && !user.getCustomer().getFullname().isBlank()) {
            return user.getCustomer().getFullname();
        }
        return user.getEmail();
    }
    // --- KẾT THÚC HELPER ---
}