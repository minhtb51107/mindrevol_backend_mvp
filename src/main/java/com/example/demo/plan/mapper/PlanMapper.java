package com.example.demo.plan.mapper;

import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanPublicResponse;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.entity.PlanStatus;
import com.example.demo.user.entity.Customer;
import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.stream.Collectors;

@Component
public class PlanMapper {

    public PlanDetailResponse toPlanDetailResponse(Plan plan) {
        if (plan == null) return null;
        
        LocalDate startDate = plan.getStartDate();
        LocalDate endDate = startDate.plusDays(plan.getDurationInDays() - 1);
        String displayStatus = calculateDisplayStatus(plan.getStatus(), endDate);

        return PlanDetailResponse.builder()
                .id(plan.getId())
                .title(plan.getTitle())
                .description(plan.getDescription())
                .durationInDays(plan.getDurationInDays())
                .dailyGoal(plan.getDailyGoal())
                .shareableLink(plan.getShareableLink())
                .status(plan.getStatus())
                .displayStatus(displayStatus) // **[THAY ĐỔI]**
                .startDate(startDate)         // **[THAY ĐỔI]**
                .endDate(endDate)             // **[THAY ĐỔI]**
                .createdAt(plan.getCreatedAt())
                .members(plan.getMembers().stream()
                        .map(this::toPlanMemberResponse)
                        .collect(Collectors.toList()))
                .build();
    }
    
 // **[HÀM HELPER MỚI]**
    private String calculateDisplayStatus(PlanStatus currentStatus, LocalDate endDate) {
        if (currentStatus != PlanStatus.ACTIVE) {
            return currentStatus.name(); // Trả về trạng thái đã lưu nếu không phải ACTIVE
        }
        if (LocalDate.now().isAfter(endDate)) {
            return "COMPLETED"; // Nếu đã qua ngày kết thúc, hiển thị là COMPLETED
        }
        return "ACTIVE"; // Mặc định là ACTIVE
    }

    public PlanPublicResponse toPlanPublicResponse(Plan plan) {
        if (plan == null) return null;

        return PlanPublicResponse.builder()
                .title(plan.getTitle())
                .description(plan.getDescription())
                .durationInDays(plan.getDurationInDays())
                .creatorFullName(getUserFullName(plan.getCreator()))
                .memberCount(plan.getMembers().size())
                .build();
    }

    private PlanDetailResponse.PlanMemberResponse toPlanMemberResponse(PlanMember member) {
        return PlanDetailResponse.PlanMemberResponse.builder()
                .userEmail(member.getUser().getEmail())
                .userFullName(getUserFullName(member.getUser()))
                .role(member.getRole().name())
                .build();
    }

    private String getUserFullName(User user) {
        if (user.getCustomer() != null) {
            return user.getCustomer().getFullname();
        }
        if (user.getEmployee() != null) {
            return user.getEmployee().getFullname();
        }
        return user.getEmail(); // Fallback
    }
}