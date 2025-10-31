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

@Component
@RequiredArgsConstructor // Sửa để inject TaskMapper
public class ProgressMapper {

    private final TaskMapper taskMapper; // Cần TaskMapper để lấy tên user

    // --- CÁC HÀM CŨ (toDailyProgressResponse, toDailyProgressSummaryResponse) BỊ XÓA ---
    // --- BẮT ĐẦU CÁC HÀM MỚI ---

    public TimelineResponse.CheckInEventResponse toCheckInEventResponse(CheckInEvent event) {
        if (event == null) {
            return null;
        }

        TimelineResponse.MemberInfo memberInfo = toMemberInfo(event.getPlanMember());

        List<TimelineResponse.AttachmentResponse> attachments = event.getAttachments() == null ? Collections.emptyList() :
                event.getAttachments().stream()
                        .map(this::toAttachmentResponse)
                        .collect(Collectors.toList());

        List<TimelineResponse.CompletedTaskInfo> tasks = event.getCompletedTasks() == null ? Collections.emptyList() :
                event.getCompletedTasks().stream()
                        .map(CheckInTask::getTask) // Lấy ra Task entity
                        .map(this::toCompletedTaskInfo) // Map sang DTO
                        .collect(Collectors.toList());

        // Sử dụng Builder để xây dựng response
        TimelineResponse.CheckInEventResponse.CheckInEventResponseBuilder builder = TimelineResponse.CheckInEventResponse.builder()
                .id(event.getId())
                .checkInTimestamp(event.getCheckInTimestamp())
                .notes(event.getNotes())
                .member(memberInfo)
                .attachments(attachments)
                .completedTasks(tasks);

        // === THÊM MỚI LOGIC MAP ===
        
        // 1. Map Links
        builder.links(event.getLinks() != null ? event.getLinks() : Collections.emptyList());

        // 2. Map Comment Count (lấy size() của Set, kiểm tra null)
        builder.commentCount(event.getComments() != null ? event.getComments().size() : 0);
        
        // 3. Map Reaction Count (lấy size() của Set, kiểm tra null)
        builder.reactionCount(event.getReactions() != null ? event.getReactions().size() : 0);
        
        // === KẾT THÚC THÊM MỚI ===

        return builder.build();
    }

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
                .userFullName(taskMapper.getUserFullName(user)) // Dùng lại helper từ TaskMapper
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