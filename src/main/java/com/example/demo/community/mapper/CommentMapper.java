package com.example.demo.community.mapper;

import com.example.demo.community.dto.response.CommentResponse;
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.Instant;

@Component // Đánh dấu đây là một Spring Bean
public class CommentMapper {

    /**
     * Chuyển đổi thủ công từ Entity ProgressComment sang DTO CommentResponse.
     * Ánh xạ chính xác các trường trong CommentResponse DTO mới.
     */
    public CommentResponse toCommentResponse(ProgressComment comment) {
        if (comment == null) {
            return null;
        }

        CommentResponse.CommentResponseBuilder builder = CommentResponse.builder();

        // Map các trường cơ bản
        builder.id(comment.getId());
        builder.content(comment.getContent());

        // SỬA LỖI 1: Sử dụng "author" (từ ProgressComment.java)
        User author = comment.getAuthor();
        if (author != null) {
            builder.authorEmail(author.getEmail());
            builder.authorId(author.getId()); // Map trường authorId
            builder.authorFullName(toAuthorFullName(author)); 
        }

        // Map ID của CheckInEvent
        if (comment.getCheckInEvent() != null) {
            builder.checkInEventId(comment.getCheckInEvent().getId()); // Map trường checkInEventId
        }

        // Map thời gian (chuyển OffsetDateTime sang Instant)
        if (comment.getCreatedAt() != null) {
            builder.createdAt(comment.getCreatedAt().toInstant()); // Map trường createdAt
        }

        return builder.build();
    }

    /**
     * Hàm helper (viết thủ công) để lấy FullName
     * (Logic này dựa trên cấu trúc User/Customer/Employee của bạn)
     */
    private String toAuthorFullName(User author) {
        if (author == null) {
            return "Người dùng ẩn danh";
        }
        
        if (author.getCustomer() != null && author.getCustomer().getFullname() != null) {
            return author.getCustomer().getFullname();
        }
        if (author.getEmployee() != null && author.getEmployee().getFullname() != null) {
            return author.getEmployee().getFullname();
        }
        // Fallback
        return author.getEmail().split("@")[0];
    }
}