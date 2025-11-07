package com.example.demo.plan.mapper;

import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanPublicResponse;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import com.example.demo.plan.dto.response.TaskResponse;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.PlanStatus;
import com.example.demo.plan.entity.Task;
import com.example.demo.user.entity.Customer;
import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.User;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PlanMapper {

	private final TaskMapper taskMapper;
	
    public PlanDetailResponse toPlanDetailResponse(Plan plan) {
        if (plan == null) return null;

        LocalDate startDate = plan.getStartDate();
        LocalDate endDate = startDate != null ? startDate.plusDays(plan.getDurationInDays() - 1) : null;
        String displayStatus = calculateDisplayStatus(plan.getStatus(), endDate);

        return PlanDetailResponse.builder()
                .id(plan.getId())
                .title(plan.getTitle())
                .description(plan.getDescription())
                // --- THÊM MAPPING MỚI ---
                .motivation(plan.getMotivation())
                // ------------------------
                .durationInDays(plan.getDurationInDays())
                .dailyGoal(plan.getDailyGoal())
                .shareableLink(plan.getShareableLink())
                .status(plan.getStatus())
                .displayStatus(displayStatus)
                .startDate(startDate)
                .endDate(endDate)
                .createdAt(plan.getCreatedAt())
                .members(plan.getMembers() == null ? Collections.emptyList() :
                         plan.getMembers().stream()
                            .map(this::toPlanMemberResponse)
                            .collect(Collectors.toList()))
                .dailyTasks(plan.getDailyTasks() == null ? Collections.emptyList() :
                            plan.getDailyTasks().stream()
                                .map(this::toTaskResponse)
                                .collect(Collectors.toList()))
                .build();
    }

    public PlanSummaryResponse toPlanSummaryResponse(PlanMember planMember) {
        if (planMember == null || planMember.getPlan() == null) return null;
        Plan plan = planMember.getPlan();
        LocalDate startDate = plan.getStartDate();
        LocalDate endDate = startDate != null ? startDate.plusDays(plan.getDurationInDays() - 1) : null;
        String displayStatus = calculateDisplayStatus(plan.getStatus(), endDate);

        return PlanSummaryResponse.builder()
                .id(plan.getId())
                .title(plan.getTitle())
                .description(plan.getDescription())
                .durationInDays(plan.getDurationInDays())
                .startDate(startDate)
                .endDate(endDate)
                .displayStatus(displayStatus)
                .shareableLink(plan.getShareableLink())
                .memberCount(plan.getMembers() == null ? 0 : plan.getMembers().size())
                .role(planMember.getRole().name())
                .build();
    }

    private String calculateDisplayStatus(PlanStatus currentStatus, LocalDate endDate) {
        if (currentStatus != PlanStatus.ACTIVE) {
            return currentStatus.name();
        }
        if (endDate != null && LocalDate.now().isAfter(endDate)) {
            return "COMPLETED";
        }
        return "ACTIVE";
    }

    public PlanPublicResponse toPlanPublicResponse(Plan plan) {
        if (plan == null) return null;

        return PlanPublicResponse.builder()
                .title(plan.getTitle())
                .description(plan.getDescription())
                .durationInDays(plan.getDurationInDays())
                .creatorFullName(getUserFullName(plan.getCreator()))
                .memberCount(plan.getMembers() == null ? 0 : plan.getMembers().size())
                .build();
    }

    public PlanDetailResponse.PlanMemberResponse toPlanMemberResponse(PlanMember member) {
        if (member == null) return null;
        User user = member.getUser();
        return PlanDetailResponse.PlanMemberResponse.builder()
                .userId(user != null ? user.getId() : null)
                .userEmail(user != null ? user.getEmail() : "N/A")
                .userFullName(getUserFullName(user))
                .role(member.getRole() != null ? member.getRole().name() : "N/A")
                .build();
    }

    private TaskResponse toTaskResponse(Task task) {
        if (task == null) return null;
        return TaskResponse.builder()
                .id(task.getId())
                .description(task.getDescription())
                .order(task.getOrder())
                .deadlineTime(task.getDeadlineTime())
                .comments(task.getComments() == null ? Collections.emptyList() :
                          task.getComments().stream()
                              .map(taskMapper::toTaskCommentResponse)
                              .collect(Collectors.toList()))
                .attachments(task.getAttachments() == null ? Collections.emptyList() :
                             task.getAttachments().stream()
                                 .map(taskMapper::toTaskAttachmentResponse)
                                 .collect(Collectors.toList()))
                .build();
    }

    private String getUserFullName(User user) {
        if (user == null) return "N/A";
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null) {
            return user.getCustomer().getFullname();
        }
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null) {
            return user.getEmployee().getFullname();
        }
        return user.getEmail();
    }
}