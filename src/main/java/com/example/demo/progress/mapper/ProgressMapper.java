package com.example.demo.progress.mapper;

import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.Task;
import com.example.demo.plan.mapper.TaskMapper;
import com.example.demo.progress.dto.response.TimelineResponse;
import com.example.demo.progress.entity.checkin.CheckInAttachment;
import com.example.demo.progress.entity.checkin.CheckInEvent;
import com.example.demo.progress.entity.checkin.CheckInTask;
import com.example.demo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

// === THÊM CÁC IMPORT ĐỂ MAP COMMENT/REACTION ===
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.community.mapper.CommentMapper; // Import mapper viết tay
import com.example.demo.community.dto.response.CommentResponse;
import com.example.demo.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.extern.slf4j.Slf4j; // THÊM IMPORT CHO LOGGING
// === KẾT THÚC THÊM IMPORT ===

@Component
@RequiredArgsConstructor
@Slf4j // THÊM ANNOTATION CHO LOGGING
public class ProgressMapper {

    private final TaskMapper taskMapper;

    // === THÊM 2 DEPENDENCIES MỚI ===
    private final CommentMapper commentMapper; // Mapper viết tay
    private final UserRepository userRepository; // Để lấy user hiện tại
    // === KẾT THÚC THÊM ===

    public TimelineResponse.CheckInEventResponse toCheckInEventResponse(CheckInEvent event) {
        if (event == null) {
            return null;
        }

        // === Lấy User ID hiện tại để tính hasCurrentUserReacted ===
        Integer currentUserId = getCurrentUserId();
        // === KẾT THÚC LẤY USER ID ===

        TimelineResponse.MemberInfo memberInfo = toMemberInfo(event.getPlanMember());

        List<TimelineResponse.AttachmentResponse> attachments = event.getAttachments() == null ? Collections.emptyList() :
                event.getAttachments().stream()
                        .map(this::toAttachmentResponse)
                        .collect(Collectors.toList());

        List<TimelineResponse.CompletedTaskInfo> tasks = event.getCompletedTasks() == null ? Collections.emptyList() :
                event.getCompletedTasks().stream()
                        .map(CheckInTask::getTask)
                        .map(this::toCompletedTaskInfo)
                        .collect(Collectors.toList());

        // === THÊM LOGIC MAP MỚI CHO COMMENT/REACTION ===

        // 1. Map danh sách Comments
        List<CommentResponse> comments = event.getComments() == null ? Collections.emptyList() :
                event.getComments().stream()
                        .map(commentMapper::toCommentResponse) // Dùng mapper viết tay
                        .collect(Collectors.toList());

        // 2. Map danh sách Reactions
        List<TimelineResponse.ReactionResponse> reactions = mapReactions(event, currentUserId);

        // 3. Lấy danh sách ID Task
        List<Long> completedTaskIds = event.getCompletedTasks() == null ? Collections.emptyList() :
                event.getCompletedTasks().stream()
                        .map(checkInTask -> checkInTask.getTask().getId())
                        .collect(Collectors.toList());
        // === KẾT THÚC LOGIC MAP MỚI ===


        // Sử dụng Builder để xây dựng response
        TimelineResponse.CheckInEventResponse.CheckInEventResponseBuilder builder = TimelineResponse.CheckInEventResponse.builder()
                .id(event.getId())
                .checkInTimestamp(event.getCheckInTimestamp())
                .notes(event.getNotes())
                .member(memberInfo)
                .attachments(attachments)
                .completedTasks(tasks)
                // CẬP NHẬT BUILDER
                .links(event.getLinks() != null ? event.getLinks() : Collections.emptyList())
                .commentCount(comments.size()) // Đếm từ list đã map
                .reactionCount(event.getReactions() != null ? event.getReactions().size() : 0) // Vẫn dùng size() cho tổng số
                .comments(comments) // TRƯỜNG MỚI
                .reactions(reactions) // TRƯỜNG MỚI
                .completedTaskIds(completedTaskIds); // Cập nhật trường này

        return builder.build();
    }

    /**
     * Helper: Map Reaction Entities thành DTOs, nhóm theo type và tính toán
     */
    private List<TimelineResponse.ReactionResponse> mapReactions(CheckInEvent event, Integer currentUserId) {
        if (event.getReactions() == null || event.getReactions().isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Nhóm các reaction theo Type
        Map<ReactionType, List<ProgressReaction>> groupedReactions = event.getReactions().stream()
                .collect(Collectors.groupingBy(ProgressReaction::getType));

        // 2. Chuyển đổi Map thành List DTO
        return groupedReactions.entrySet().stream()
                .map(entry -> {
                    ReactionType type = entry.getKey();
                    List<ProgressReaction> reactionsOfType = entry.getValue();

                    // 3. Kiểm tra xem user hiện tại có react type này không
                    boolean hasCurrentUserReacted = reactionsOfType.stream()
                            .anyMatch(reaction -> reaction.getUser().getId().equals(currentUserId));

                    return TimelineResponse.ReactionResponse.builder()
                            .type(type)
                            .count(reactionsOfType.size())
                            .hasCurrentUserReacted(hasCurrentUserReacted)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper: Lấy ID của user đang đăng nhập (nếu có)
     */
    private Integer getCurrentUserId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetails) {
                String email = ((UserDetails) principal).getUsername();
                // Chúng ta cần ID, không phải email
                return userRepository.findByEmail(email)
                        .map(User::getId)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Không thể lấy thông tin user hiện tại: {}", e.getMessage());
        }
        return null; // Trả về null nếu không xác định được
    }

    // --- CÁC HÀM MAP CŨ (toMemberInfo, toAttachmentResponse, toCompletedTaskInfo) (Giữ nguyên) ---

    public TimelineResponse.MemberInfo toMemberInfo(PlanMember member) {
        if (member == null || member.getUser() == null) {
            return TimelineResponse.MemberInfo.builder()
                    .userId(null)
                    .userEmail("N/A")
                    .userFullName("Người dùng ẩn danh")
                    .build();
        }
        User user = member.getUser();
        return TimelineResponse.MemberInfo.builder()
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userFullName(taskMapper.getUserFullName(user))
                .build();
    }

    public TimelineResponse.AttachmentResponse toAttachmentResponse(CheckInAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        return TimelineResponse.AttachmentResponse.builder()
                .fileUrl(attachment.getFileUrl())
                .originalFilename(attachment.getOriginalFilename())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .build();
    }

    public TimelineResponse.CompletedTaskInfo toCompletedTaskInfo(Task task) {
        if (task == null) {
            return null;
        }
        return TimelineResponse.CompletedTaskInfo.builder()
                .taskId(task.getId())
                .description(task.getDescription())
                .build();
    }
}
