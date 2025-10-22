package com.example.demo.progress.mapper;

// *** Không cần import AttachmentResponse và EvidenceAttachment ở đây ***
import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.dto.response.DailyProgressSummaryResponse;
import com.example.demo.progress.entity.DailyProgress;
import org.springframework.stereotype.Component;

import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.user.entity.User;
import java.util.ArrayList; // Giữ lại nếu còn dùng evidence links trong DailyProgress entity
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
                    if (reactionsOfType.isEmpty()) return null;
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

        Set<Long> completedTaskIds = dailyProgress.getCompletedTaskIds() == null ?
                                        Collections.emptySet() :
                                        new HashSet<>(dailyProgress.getCompletedTaskIds());

        // *** BỎ HOÀN TOÀN LOGIC MAP ATTACHMENTS TỪ DAILYPROGRESS ***

        return DailyProgressResponse.builder()
                .id(dailyProgress.getId())
                .date(dailyProgress.getDate())
                .completed(dailyProgress.isCompleted())
                .notes(dailyProgress.getNotes())
                // *** BỎ evidence VÀ attachments Ở ĐÂY ***
                // Nếu DailyProgressResponse *vẫn còn* trường evidence (links chung):
                // .evidence(dailyProgress.getEvidence() == null ? new ArrayList<>() : new ArrayList<>(dailyProgress.getEvidence()))
                .comments(comments)
                .reactions(reactions)
                .completedTaskIds(completedTaskIds)
                .build();
    }

    public DailyProgressResponse toDailyProgressResponse(DailyProgress dailyProgress) {
        return toDailyProgressResponse(dailyProgress, null);
    }

    public DailyProgressResponse.CommentResponse toCommentResponse(ProgressComment comment) {
         if (comment == null) return null;
         User author = comment.getAuthor();
         return DailyProgressResponse.CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .authorEmail(author != null ? author.getEmail() : "N/A")
                .authorFullName(author != null ? getUserFullName(author) : "Người dùng ẩn danh")
                .build();
    }

    // *** BỎ HOÀN TOÀN PHƯƠNG THỨC toAttachmentResponse ***

    public String getUserFullName(User user) {
        if (user == null) return "N/A";
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null && !user.getEmployee().getFullname().isBlank()) {
            return user.getEmployee().getFullname();
        }
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null && !user.getCustomer().getFullname().isBlank()) {
            return user.getCustomer().getFullname();
        }
        return user.getEmail();
    }

    public DailyProgressSummaryResponse toDailyProgressSummaryResponse(DailyProgress dailyProgress, Integer currentUserId) {
        if (dailyProgress == null) {
            return null;
        }

        // Gọi toDailyProgressResponse để tận dụng logic mapping đã có
        DailyProgressResponse tempResponse = toDailyProgressResponse(dailyProgress, currentUserId);

        return DailyProgressSummaryResponse.builder()
                .id(dailyProgress.getId())
                .completed(dailyProgress.isCompleted())
                .notes(dailyProgress.getNotes())
                // *** BỎ evidence VÀ attachments Ở ĐÂY ***
                // Nếu DailyProgressSummaryResponse *vẫn còn* trường evidence (links chung):
                // .evidence(tempResponse.getEvidence())
                .comments(tempResponse.getComments())
                .reactions(tempResponse.getReactions())
                .completedTaskIds(tempResponse.getCompletedTaskIds())
                .build();
    }
}