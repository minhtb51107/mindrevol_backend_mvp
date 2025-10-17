package com.example.demo.progress.mapper;

import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.entity.DailyProgress;
import org.springframework.stereotype.Component;

import com.example.demo.community.entity.ProgressComment;
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.user.entity.User;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ProgressMapper {

    /**
     * Phương thức chính để chuyển đổi, nhận thêm ID của người dùng hiện tại.
     * @param dailyProgress Đối tượng tiến độ từ database.
     * @param currentUserId ID của người dùng đang thực hiện request.
     * @return DTO đã được tối ưu cho việc hiển thị.
     */
    public DailyProgressResponse toDailyProgressResponse(DailyProgress dailyProgress, Integer currentUserId) {
        if (dailyProgress == null) {
            return null;
        }

        // 1. Map danh sách bình luận (giữ nguyên)
        var comments = dailyProgress.getComments().stream()
                .map(this::toCommentResponse)
                .collect(Collectors.toList());
        
        // 2. Map danh sách reactions với logic đã tối ưu
        var reactions = Arrays.stream(ReactionType.values())
                .map(type -> {
                    List<ProgressReaction> reactionsOfType = dailyProgress.getReactions().stream()
                            .filter(r -> r.getType() == type)
                            .collect(Collectors.toList());

                    if (reactionsOfType.isEmpty()) {
                        return null;
                    }
                    
                    // **[LOGIC TỐI ƯU]** Kiểm tra xem người dùng hiện tại có trong danh sách reaction không
                    boolean hasCurrentUserReacted = reactionsOfType.stream()
                            .anyMatch(r -> r.getUser().getId().equals(currentUserId));

                    // **[DTO TỐI ƯU]** Trả về DTO mới
                    return DailyProgressResponse.ReactionSummaryResponse.builder()
                            .type(type.name())
                            .count(reactionsOfType.size())
                            .hasCurrentUserReacted(hasCurrentUserReacted) // Thay thế cho list emails
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return DailyProgressResponse.builder()
                .id(dailyProgress.getId())
                .date(dailyProgress.getDate())
                .completed(dailyProgress.isCompleted())
                .notes(dailyProgress.getNotes())
                .evidence(dailyProgress.getEvidence())
                .comments(comments)
                .reactions(reactions) // Sử dụng danh sách reactions đã tối ưu
                .build();
    }
    
    /**
     * Phương thức cũ, không còn được khuyến khích sử dụng trực tiếp
     * khi cần thông tin reaction của người dùng hiện tại.
     */
    public DailyProgressResponse toDailyProgressResponse(DailyProgress dailyProgress) {
        // Gọi phương thức mới với currentUserId là null để đảm bảo tương thích
        // và không gây lỗi ở những nơi chưa được cập nhật.
        return toDailyProgressResponse(dailyProgress, null);
    }
    
    public DailyProgressResponse.CommentResponse toCommentResponse(ProgressComment comment) {
        return DailyProgressResponse.CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .authorEmail(comment.getAuthor().getEmail())
                .authorFullName(getUserFullName(comment.getAuthor()))
                .build();
    }

    public String getUserFullName(User user) {
        if (user.getCustomer() != null) return user.getCustomer().getFullname();
        if (user.getEmployee() != null) return user.getEmployee().getFullname();
        return user.getEmail();
    }
}