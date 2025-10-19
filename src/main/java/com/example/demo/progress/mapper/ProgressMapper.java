package com.example.demo.progress.mapper;

import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.dto.response.DailyProgressSummaryResponse;
import com.example.demo.progress.entity.DailyProgress;
import org.springframework.stereotype.Component;

import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.user.entity.User;
import java.util.ArrayList; // Thêm import
import java.util.Arrays;
import java.util.Collections; // Thêm import
import java.util.HashSet; // Thêm import
import java.util.List;
import java.util.Objects;
import java.util.Set; // Thêm import
import java.util.stream.Collectors;

@Component
public class ProgressMapper {

    public DailyProgressResponse toDailyProgressResponse(DailyProgress dailyProgress, Integer currentUserId) {
        if (dailyProgress == null) {
            return null;
        }

        List<DailyProgressResponse.CommentResponse> comments = dailyProgress.getComments() == null ? Collections.emptyList() :
                dailyProgress.getComments().stream()
                    .map(this::toCommentResponse)
                    .collect(Collectors.toList());

        List<DailyProgressResponse.ReactionSummaryResponse> reactions = dailyProgress.getReactions() == null ? Collections.emptyList() :
            Arrays.stream(ReactionType.values())
                .map(type -> {
                    List<ProgressReaction> reactionsOfType = dailyProgress.getReactions().stream()
                            .filter(r -> r.getType() == type)
                            .collect(Collectors.toList());

                    if (reactionsOfType.isEmpty()) {
                        return null;
                    }

                    boolean hasCurrentUserReacted = currentUserId != null && reactionsOfType.stream()
                            .anyMatch(r -> r.getUser() != null && r.getUser().getId().equals(currentUserId));

                    return DailyProgressResponse.ReactionSummaryResponse.builder()
                            .type(type.name())
                            .count(reactionsOfType.size())
                            .hasCurrentUserReacted(hasCurrentUserReacted)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<Integer> completedIndices = dailyProgress.getCompletedTaskIndices() == null ?
                                         Collections.emptySet() :
                                         new HashSet<>(dailyProgress.getCompletedTaskIndices()); // Tạo bản sao

        return DailyProgressResponse.builder()
                .id(dailyProgress.getId())
                .date(dailyProgress.getDate())
                .completed(dailyProgress.isCompleted())
                .notes(dailyProgress.getNotes())
                .evidence(dailyProgress.getEvidence())
                .comments(comments)
                .reactions(reactions)
                .completedTaskIndices(completedIndices) // Thêm trường này
                .build();
    }

    public DailyProgressResponse toDailyProgressResponse(DailyProgress dailyProgress) {
        return toDailyProgressResponse(dailyProgress, null);
    }

    public DailyProgressResponse.CommentResponse toCommentResponse(ProgressComment comment) {
         if (comment == null) return null; // Thêm kiểm tra null
         User author = comment.getAuthor(); // Lấy author ra biến
         return DailyProgressResponse.CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .authorEmail(author != null ? author.getEmail() : "N/A") // Kiểm tra null
                .authorFullName(author != null ? getUserFullName(author) : "Người dùng ẩn danh") // Kiểm tra null
                .build();
    }

    public String getUserFullName(User user) {
        if (user == null) return "N/A";
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null) return user.getCustomer().getFullname();
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null) return user.getEmployee().getFullname();
        return user.getEmail();
    }

    public DailyProgressSummaryResponse toDailyProgressSummaryResponse(DailyProgress dailyProgress, Integer currentUserId) {
        if (dailyProgress == null) {
            return null;
        }

        DailyProgressResponse tempResponse = toDailyProgressResponse(dailyProgress, currentUserId);

        Set<Integer> completedIndices = dailyProgress.getCompletedTaskIndices() == null ?
                                         Collections.emptySet() :
                                         new HashSet<>(dailyProgress.getCompletedTaskIndices()); // Tạo bản sao

        return DailyProgressSummaryResponse.builder()
                .id(dailyProgress.getId())
                .completed(dailyProgress.isCompleted())
                .notes(dailyProgress.getNotes())
                .evidence(dailyProgress.getEvidence())
                .comments(tempResponse.getComments())
                .reactions(tempResponse.getReactions())
                .completedTaskIndices(completedIndices) // Thêm trường này
                .build();
    }
}