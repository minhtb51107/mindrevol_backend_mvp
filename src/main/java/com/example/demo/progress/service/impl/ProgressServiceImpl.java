package com.example.demo.progress.service.impl;

import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.progress.dto.request.LogProgressRequest;
import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.dto.response.DailyProgressSummaryResponse;
import com.example.demo.progress.dto.response.ProgressDashboardResponse;
import com.example.demo.progress.entity.DailyProgress;
import com.example.demo.progress.mapper.ProgressMapper;
import com.example.demo.progress.repository.DailyProgressRepository;
import com.example.demo.progress.service.ProgressService;
import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects; // Thêm import
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgressServiceImpl implements ProgressService {

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanMemberRepository planMemberRepository;
    private final DailyProgressRepository dailyProgressRepository;
    private final ProgressMapper progressMapper;

    @Override
    public DailyProgressResponse logOrUpdateDailyProgress(String shareableLink, String userEmail, LogProgressRequest request) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        PlanMember member = planMemberRepository.findByPlanIdAndUserId(plan.getId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException("Bạn không phải là thành viên của kế hoạch này."));

        validateCheckinDate(request.getDate(), plan);

        DailyProgress progress = dailyProgressRepository.findByPlanMemberIdAndDate(member.getId(), request.getDate())
                .orElse(new DailyProgress());

        // Kiểm tra xem có đang cố gắng sửa log của ngày quá xa không (ví dụ: chỉ cho sửa trong vòng 2 ngày)
        if (progress.getId() != null && progress.getDate().isBefore(LocalDate.now().minusDays(2))) {
             throw new BadRequestException("Không thể sửa đổi tiến độ cho ngày đã quá cũ.");
        }


        progress.setPlanMember(member);
        progress.setDate(request.getDate());
        progress.setNotes(request.getNotes());
        progress.setEvidence(request.getEvidence());

        int totalTasks = plan.getDailyTasks() != null ? plan.getDailyTasks().size() : 0;
        Set<Integer> validIndices = new HashSet<>();

        if (request.getCompletedTaskIndices() != null) {
            validIndices = request.getCompletedTaskIndices().stream()
                .filter(index -> index != null && index >= 0 && index < totalTasks)
                .collect(Collectors.toSet());
        }
        progress.setCompletedTaskIndices(validIndices);

        // Cập nhật trạng thái completed dựa trên tasks (nếu có)
        if (totalTasks > 0) {
            progress.setCompleted(validIndices.size() == totalTasks);
        } else {
            // Nếu không có task, dùng giá trị completed từ request
            progress.setCompleted(request.getCompleted());
        }

        // Validation thêm: Không cho phép completed=false nếu tất cả task đã check
        if (totalTasks > 0 && validIndices.size() == totalTasks && !request.getCompleted()) {
             // Người dùng cố tình bỏ check ô "Hoàn thành tất cả" nhưng vẫn check hết task con?
             // Có thể log cảnh báo hoặc giữ nguyên completed = true
             progress.setCompleted(true); // Ghi đè lại thành true
             // Hoặc throw new BadRequestException("Trạng thái không hợp lệ: Tất cả công việc đã hoàn thành.");
        }


        DailyProgress savedProgress = dailyProgressRepository.save(progress);
        return progressMapper.toDailyProgressResponse(savedProgress, user.getId());
    }

    private void validateCheckinDate(LocalDate checkinDate, Plan plan) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1); // Thêm dòng này
        LocalDate planStartDate = plan.getStartDate();
        LocalDate planEndDate = planStartDate.plusDays(plan.getDurationInDays() - 1);

        if (checkinDate.isAfter(today)) {
            throw new BadRequestException("Không thể ghi nhận tiến độ cho một ngày trong tương lai.");
        }

        // Kích hoạt lại kiểm tra: Chỉ cho check hôm nay hoặc hôm qua
        if (!checkinDate.isEqual(today) && !checkinDate.isEqual(yesterday)) {
             throw new BadRequestException("Bạn chỉ có thể ghi nhận tiến độ cho hôm nay hoặc hôm qua.");
        }

        if (checkinDate.isBefore(planStartDate) || checkinDate.isAfter(planEndDate)) {
            throw new BadRequestException("Ngày check-in không nằm trong thời gian diễn ra kế hoạch.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressDashboardResponse getProgressDashboard(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        if (plan.getMembers() == null || plan.getMembers().stream().noneMatch(m -> m.getUser() != null && m.getUser().getId().equals(user.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền xem tiến độ của kế hoạch này.");
        }

        List<ProgressDashboardResponse.MemberProgressResponse> membersProgress = plan.getMembers().stream()
                .map(member -> buildMemberProgress(member, plan, user.getId()))
                .filter(Objects::nonNull) // Lọc bỏ member null nếu có lỗi
                .collect(Collectors.toList());

        return ProgressDashboardResponse.builder()
                .planTitle(plan.getTitle())
                .membersProgress(membersProgress)
                .build();
    }

    private ProgressDashboardResponse.MemberProgressResponse buildMemberProgress(PlanMember member, Plan plan, Integer currentUserId) {
        if (member == null || member.getUser() == null) return null; // Thêm kiểm tra null

        List<DailyProgress> allProgress = member.getDailyProgressList() == null ? Collections.emptyList() : member.getDailyProgressList();


        long completedDays = allProgress.stream().filter(DailyProgress::isCompleted).count();
        double completionPercentage = (plan.getDurationInDays() > 0) ? ((double) completedDays / plan.getDurationInDays() * 100) : 0;

        LocalDate planStartDate = plan.getStartDate();
        if (planStartDate == null) return null; // Thêm kiểm tra null

        Map<LocalDate, DailyProgress> progressByDate = allProgress.stream()
                .collect(Collectors.toMap(DailyProgress::getDate, p -> p));

        Map<String, DailyProgressSummaryResponse> dailyStatus = IntStream.range(0, plan.getDurationInDays())
            .mapToObj(planStartDate::plusDays)
            .collect(Collectors.toMap(
                LocalDate::toString,
                date -> {
                    DailyProgress progress = progressByDate.get(date);

                    if (progress == null) {
                        return DailyProgressSummaryResponse.builder()
                                .id(null)
                                .completed(false)
                                .notes(null)
                                .evidence(null)
                                .comments(Collections.emptyList())
                                .reactions(Collections.emptyList())
                                .completedTaskIndices(Collections.emptySet())
                                .build();
                    }

                    return progressMapper.toDailyProgressSummaryResponse(progress, currentUserId);
                },
                (v1, v2) -> v1, // Giữ giá trị đầu tiên nếu có key trùng (không nên xảy ra)
                LinkedHashMap::new // Giữ đúng thứ tự ngày
            ));

        return ProgressDashboardResponse.MemberProgressResponse.builder()
                .userEmail(member.getUser().getEmail())
                .userFullName(getUserFullName(member.getUser()))
                .completedDays((int) completedDays)
                .completionPercentage(completionPercentage)
                .dailyStatus(dailyStatus)
                .build();
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    private Plan findPlanByShareableLink(String link) {
        return planRepository.findByShareableLink(link)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
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