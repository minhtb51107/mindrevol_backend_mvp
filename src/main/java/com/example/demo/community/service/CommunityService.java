package com.example.demo.community.service;

import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.dto.response.CommentResponse;
// (Không cần import DTO của DailyProgressResponse nữa)

public interface CommunityService {

    /**
     * Đăng một bình luận mới vào một CheckInEvent cụ thể.
     * THAY ĐỔI: progressId -> checkInEventId
     */
    CommentResponse postComment(Long checkInEventId, String userEmail, PostCommentRequest request);

    /**
     * Cập nhật một bình luận đã có.
     */
    CommentResponse updateComment(Long commentId, String userEmail, UpdateCommentRequest request);

    /**
     * Xóa một bình luận.
     */
    void deleteComment(Long commentId, String userEmail);

    /**
     * Thêm/cập nhật một cảm xúc vào một CheckInEvent cụ thể.
     * THAY ĐỔI: progressId -> checkInEventId
     */
    void addOrUpdateReaction(Long checkInEventId, String userEmail, AddReactionRequest request);

    /**
     * Xóa một cảm xúc khỏi một CheckInEvent cụ thể.
     * THAY ĐỔI: progressId -> checkInEventId
     */
    void removeReaction(Long checkInEventId, String userEmail);
    
}