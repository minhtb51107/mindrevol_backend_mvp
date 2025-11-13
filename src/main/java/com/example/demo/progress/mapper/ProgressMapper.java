package com.example.demo.progress.mapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set; 
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.example.demo.community.dto.response.CommentResponse;
import com.example.demo.community.entity.ProgressComment; 
import com.example.demo.community.entity.ProgressReaction;
import com.example.demo.community.entity.ReactionType;
import com.example.demo.community.mapper.CommentMapper; 
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.Task;
import com.example.demo.plan.mapper.TaskMapper;
import com.example.demo.progress.dto.response.TimelineResponse;
// SỬA LỖI 4: Import cả 2 loại AttachmentResponse
import com.example.demo.progress.dto.response.AttachmentResponse; // File riêng
import com.example.demo.progress.dto.response.LogResponseDto;
import com.example.demo.progress.entity.checkin.CheckInAttachment;
import com.example.demo.progress.entity.checkin.CheckInEvent;
import com.example.demo.progress.entity.checkin.CheckInTask;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; 

@Component
@RequiredArgsConstructor
@Slf4j 
public class ProgressMapper {

    private final TaskMapper taskMapper;
    private final CommentMapper commentMapper; 
    private final UserRepository userRepository; 

    public TimelineResponse.CheckInEventResponse toCheckInEventResponse(CheckInEvent event) {
        if (event == null) {
            return null;
        }

        Integer currentUserId = getCurrentUserId();
        TimelineResponse.MemberInfo memberInfo = toMemberInfo(event.getPlanMember());

        List<TimelineResponse.AttachmentResponse> attachments = event.getAttachments() == null ? Collections.emptyList() :
                event.getAttachments().stream()
                        .map(this::toAttachmentResponse) // Dùng helper cho Timeline
                        .collect(Collectors.toList());

        List<TimelineResponse.CompletedTaskInfo> tasks = event.getCompletedTasks() == null ? Collections.emptyList() :
            event.getCompletedTasks().stream()
                    .map(CheckInTask::getTask)
                    .filter(java.util.Objects::nonNull) 
                    .map(this::toCompletedTaskInfo)
                    .collect(Collectors.toList());

        List<CommentResponse> comments = event.getComments() == null ? Collections.emptyList() :
                event.getComments().stream()
                        .map(commentMapper::toCommentResponse) 
                        .collect(Collectors.toList());

        List<TimelineResponse.ReactionResponse> reactions = mapReactions(event.getReactions(), currentUserId); // Sửa (chỉ truyền Set)

        List<Long> completedTaskIds = event.getCompletedTasks() == null ? Collections.emptyList() :
                event.getCompletedTasks().stream()
                        .map(CheckInTask::getTask) 
                        .filter(java.util.Objects::nonNull) 
                        .map(Task::getId) 
                        .collect(Collectors.toList());

        TimelineResponse.CheckInEventResponse.CheckInEventResponseBuilder builder = TimelineResponse.CheckInEventResponse.builder()
                .id(event.getId())
                .checkInTimestamp(event.getCheckInTimestamp())
                .notes(event.getNotes())
                .member(memberInfo)
                .attachments(attachments)
                .completedTasks(tasks)
                .links(event.getLinks() != null ? event.getLinks() : Collections.emptyList())
                .commentCount(comments.size()) 
                .reactionCount(event.getReactions() != null ? event.getReactions().size() : 0) 
                .comments(comments) 
                .reactions(reactions) 
                .completedTaskIds(completedTaskIds);

        return builder.build();
    }

    private List<TimelineResponse.ReactionResponse> mapReactions(Set<ProgressReaction> reactionSet, Integer currentUserId) {
        if (reactionSet == null || reactionSet.isEmpty()) {
            return Collections.emptyList();
        }

        Map<ReactionType, List<ProgressReaction>> groupedReactions = reactionSet.stream()
                .collect(Collectors.groupingBy(ProgressReaction::getType));

        return groupedReactions.entrySet().stream()
                .map(entry -> {
                    ReactionType type = entry.getKey();
                    List<ProgressReaction> reactionsOfType = entry.getValue();

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

    private Integer getCurrentUserId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetails) {
                String email = ((UserDetails) principal).getUsername();
                return userRepository.findByEmail(email)
                        .map(User::getId)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Không thể lấy thông tin user hiện tại: {}", e.getMessage());
        }
        return null;
    }

    // SỬA LỖI 1: Xóa .userAvatar (Đã đúng)
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
                .userId(user.getId()) // Giữ Integer
                .userEmail(user.getEmail())
                .userFullName(taskMapper.getUserFullName(user))
                // .userAvatar(user.getPhotoUrl()) // <-- ĐÃ XÓA
                .build();
    }

    // Helper này trả về DTO lồng của TimelineResponse
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
    
    // *** BẮT ĐẦU PHẦN SỬA LỖI P4 (THÊM HELPER + MAPPER MỚI) ***

    // SỬA LỖI 4: Helper mới trả về DTO (file riêng)
    public AttachmentResponse toStandaloneAttachmentResponse(CheckInAttachment attachment) {
        if (attachment == null) return null;
        return AttachmentResponse.builder()
                .fileUrl(attachment.getFileUrl())
                .originalFilename(attachment.getOriginalFilename())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .build();
    }

    // SỬA LỖI 3 & 4: Helper plural (public) dùng helper (standalone)
    public List<AttachmentResponse> mapAttachmentsToStandaloneResponses(Set<CheckInAttachment> attachments) {
        if (attachments == null) return Collections.emptyList();
        return attachments.stream()
                .map(this::toStandaloneAttachmentResponse)
                .collect(Collectors.toList());
    }

    // SỬA LỖI 3: Helper plural (public)
    public List<TimelineResponse.CompletedTaskInfo> mapCheckInTasksToTaskDtos(Set<CheckInTask> tasks) {
        if (tasks == null) return Collections.emptyList();
        return tasks.stream()
                .map(CheckInTask::getTask)
                .filter(java.util.Objects::nonNull)
                .map(this::toCompletedTaskInfo)
                .collect(Collectors.toList());
    }

    // SỬA LỖI 3: Helper plural (public)
    public List<TimelineResponse.ReactionResponse> mapReactionsToReactionDtos(Set<ProgressReaction> reactions, User currentUser) {
        Integer currentUserId = (currentUser != null) ? currentUser.getId() : null;
        return mapReactions(reactions, currentUserId); // Tái sử dụng hàm mapReactions đã có
    }

    // SỬA LỖI 3: Helper plural (public)
    public List<CommentResponse> mapCommentsToCommentResponses(Set<ProgressComment> comments, User currentUser) {
        if (comments == null) return Collections.emptyList();
        return comments.stream()
                .map(commentMapper::toCommentResponse) // Tái sử dụng CommentMapper
                .collect(Collectors.toList());
    }

    /**
     * Hàm mới để map CheckInEvent sang LogResponseDto (SỬA LỖI 1, 2, 3, 4)
     */
    public LogResponseDto mapCheckInEventToLogResponseDto(CheckInEvent event, User currentUser) {
        if (event == null) return null;

        return LogResponseDto.builder()
                .id(event.getId())
                .notes(event.getNotes())
                .checkInTimestamp(event.getCheckInTimestamp()) // SỬA LỖI 2
                // .createdAt/updatedAt bị xóa
                .links(event.getLinks())
                .author(mapUserToAuthorDto(event.getPlanMember().getUser())) // SỬA LỖI 2
                .attachments(mapAttachmentsToStandaloneResponses(event.getAttachments())) // SỬA LỖI 3 & 4
                .completedTasks(mapCheckInTasksToTaskDtos(event.getCompletedTasks())) // SỬA LỖI 3
                .reactions(mapReactionsToReactionDtos(event.getReactions(), currentUser)) // SỬA LỖI 3
                .comments(mapCommentsToCommentResponses(event.getComments(), currentUser)) // SỬA LỖI 3
                .commentCount(event.getComments() != null ? event.getComments().size() : 0)
                .build();
    }

    /**
     * Hàm helper thủ công cho map Author (SỬA LỖI 1 & 5)
     */
    public LogResponseDto.AuthorDto mapUserToAuthorDto(User user) {
        if (user == null) return null;
        return LogResponseDto.AuthorDto.builder()
                .userId(user.getId().longValue()) // Dùng longValue()
                .userFullName(user.getFullname())
                .userAvatar(user.getPhoto()) // SỬA LỖI 5: Dùng getPhotoUrl()
                .build();
    }
    // *** KẾT THÚC PHẦN SỬA LỖI P4 ***
}