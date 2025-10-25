package com.example.demo.progress.mapper;

import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.dto.response.DailyProgressSummaryResponse;
import com.example.demo.progress.entity.DailyProgress;
import org.springframework.stereotype.Component;
import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.user.entity.User;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProgressMapper {

    /**
     * Chuyển đổi DailyProgress sang DailyProgressResponse.
     * Luôn trả về một đối tượng DTO (có thể rỗng), không bao giờ trả về null.
     */
    public DailyProgressResponse toDailyProgressResponse(DailyProgress dailyProgress, Integer currentUserId) {
        // Nếu input là null, trả về một DTO rỗng (KHÔNG NULL)
        if (dailyProgress == null) {
            return DailyProgressResponse.builder()
                    .id(null)
                    .date(null) // Date sẽ được lấy từ key của map trong service
                    .completed(false)
                    .notes(null)
                    .attachments(Collections.emptyList()) // Sử dụng Collections.emptyList()
                    .comments(Collections.emptyList())
                    .reactions(Collections.emptyList())
                    .completedTaskIds(Collections.emptySet()) // Sử dụng Collections.emptySet()
                    .build();
        }

        List<DailyProgressResponse.CommentResponse> comments = dailyProgress.getComments() == null ? Collections.emptyList() :
                dailyProgress.getComments().stream()
                    .map(this::toCommentResponse)
                    .filter(Objects::nonNull) // Lọc bỏ comment null nếu có lỗi mapper con
                    .collect(Collectors.toList());

        List<DailyProgressResponse.ReactionSummaryResponse> reactions = dailyProgress.getReactions() == null ? Collections.emptyList() :
            Arrays.stream(ReactionType.values())
                .map(type -> {
                    List<ProgressReaction> reactionsOfType = dailyProgress.getReactions().stream()
                            .filter(r -> r.getType() == type && r.getUser() != null) // Thêm check user not null
                            .collect(Collectors.toList());
                    if (reactionsOfType.isEmpty()) return null;
                    boolean hasCurrentUserReacted = currentUserId != null && reactionsOfType.stream()
                            .anyMatch(r -> r.getUser().getId().equals(currentUserId));
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


        return DailyProgressResponse.builder()
                .id(dailyProgress.getId())
                .date(dailyProgress.getDate())
                .completed(dailyProgress.isCompleted())
                .notes(dailyProgress.getNotes())
                // .evidence(...) // Bỏ nếu không dùng
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

    public String getUserFullName(User user) {
        if (user == null) return "N/A";
        // Ưu tiên Customer trước vì đây là user cuối
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null && !user.getCustomer().getFullname().isBlank()) {
            return user.getCustomer().getFullname();
        }
        // Sau đó Employee
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null && !user.getEmployee().getFullname().isBlank()) {
            return user.getEmployee().getFullname();
        }
        return user.getEmail(); // Fallback về email
    }

    /**
     * Chuyển đổi DailyProgress sang DailyProgressSummaryResponse.
     * Luôn trả về một đối tượng DTO (có thể rỗng), không bao giờ trả về null.
     */
    public DailyProgressSummaryResponse toDailyProgressSummaryResponse(DailyProgress dailyProgress, Integer currentUserId) {
        // Gọi toDailyProgressResponse (đã được sửa để không trả về null)
        DailyProgressResponse tempResponse = toDailyProgressResponse(dailyProgress, currentUserId);

        // tempResponse sẽ không bao giờ null nữa, nó sẽ là DTO rỗng nếu dailyProgress là null
        return DailyProgressSummaryResponse.builder()
                .id(tempResponse.getId())
                .completed(tempResponse.isCompleted())
                .notes(tempResponse.getNotes())
                .attachments(tempResponse.getAttachments())
                .comments(tempResponse.getComments())
                .reactions(tempResponse.getReactions())
                .completedTaskIds(tempResponse.getCompletedTaskIds())
                .build();
    }
}