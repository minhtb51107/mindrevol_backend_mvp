package com.example.demo.community.service;

import com.example.demo.community.dto.request.AddReactionRequest;
import com.example.demo.community.dto.request.PostCommentRequest;
import com.example.demo.community.dto.request.UpdateCommentRequest;
import com.example.demo.community.dto.response.CommentResponse;
import com.example.demo.progress.dto.response.DailyProgressResponse;

public interface CommunityService {
    CommentResponse postComment(Long progressId, String userEmail, PostCommentRequest request);
    void addOrUpdateReaction(Long progressId, String userEmail, AddReactionRequest request);
    void removeReaction(Long progressId, String userEmail);
    CommentResponse updateComment(Long commentId, String userEmail, UpdateCommentRequest request);
    void deleteComment(Long commentId, String userEmail);
}