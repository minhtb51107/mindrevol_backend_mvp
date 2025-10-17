package com.example.demo.progress.service.impl;

import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.progress.dto.request.LogProgressRequest;
import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.dto.response.ProgressDashboardResponse;
import com.example.demo.progress.entity.DailyProgress;
import com.example.demo.progress.mapper.ProgressMapper;
import com.example.demo.progress.repository.DailyProgressRepository;
import com.example.demo.progress.service.ProgressService;
import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.Customer;
import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        
     // **[LOGIC MỚI]** Ngăn chặn gian lận và kiểm tra ngày hợp lệ
        validateCheckinDate(request.getDate(), plan);

        // Validate ngày check-in
        LocalDate planStartDate = plan.getCreatedAt().toLocalDate();
        if (request.getDate().isBefore(planStartDate) || request.getDate().isAfter(planStartDate.plusDays(plan.getDurationInDays() - 1))) {
            throw new BadRequestException("Ngày check-in không nằm trong thời gian diễn ra kế hoạch.");
        }

        DailyProgress progress = dailyProgressRepository.findByPlanMemberIdAndDate(member.getId(), request.getDate())
                .orElse(new DailyProgress());

        progress.setPlanMember(member);
        progress.setDate(request.getDate());
        progress.setCompleted(request.getCompleted());
        progress.setNotes(request.getNotes());
        progress.setEvidence(request.getEvidence());

        DailyProgress savedProgress = dailyProgressRepository.save(progress);
        return progressMapper.toDailyProgressResponse(savedProgress);
    }
    
 // **[HÀM HELPER MỚI]**
    private void validateCheckinDate(LocalDate checkinDate, Plan plan) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate planStartDate = plan.getStartDate();
        LocalDate planEndDate = planStartDate.plusDays(plan.getDurationInDays() - 1);

        // 1. Không cho check-in cho ngày trong tương lai
        if (checkinDate.isAfter(today)) {
            throw new BadRequestException("Không thể ghi nhận tiến độ cho một ngày trong tương lai.");
        }

        // 2. Chỉ cho phép check-in cho "hôm nay" và "hôm qua"
        if (!checkinDate.isEqual(today) && !checkinDate.isEqual(yesterday)) {
             throw new BadRequestException("Bạn chỉ có thể ghi nhận tiến độ cho hôm nay hoặc hôm qua.");
        }
        
        // 3. Ngày check-in phải nằm trong khoảng thời gian của kế hoạch
        if (checkinDate.isBefore(planStartDate) || checkinDate.isAfter(planEndDate)) {
            throw new BadRequestException("Ngày check-in không nằm trong thời gian diễn ra kế hoạch.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressDashboardResponse getProgressDashboard(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        // Đảm bảo người xem là thành viên của kế hoạch
        if (plan.getMembers().stream().noneMatch(m -> m.getUser().getId().equals(user.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền xem tiến độ của kế hoạch này.");
        }

        List<ProgressDashboardResponse.MemberProgressResponse> membersProgress = plan.getMembers().stream()
                .map(member -> buildMemberProgress(member, plan))
                .collect(Collectors.toList());

        return ProgressDashboardResponse.builder()
                .planTitle(plan.getTitle())
                .membersProgress(membersProgress)
                .build();
    }

    private ProgressDashboardResponse.MemberProgressResponse buildMemberProgress(PlanMember member, Plan plan) {
        List<DailyProgress> allProgress = member.getDailyProgressList();
        
        long completedDays = allProgress.stream().filter(DailyProgress::isCompleted).count();
        double completionPercentage = (double) completedDays / plan.getDurationInDays() * 100;

     // **[THAY ĐỔI]** Sử dụng startDate thay vì createdAt
        LocalDate planStartDate = plan.getStartDate();
        
        // Dùng LinkedHashMap để giữ đúng thứ tự ngày
        Map<String, Boolean> dailyStatus = IntStream.range(0, plan.getDurationInDays())
            .mapToObj(planStartDate::plusDays)
            .collect(Collectors.toMap(
                LocalDate::toString,
                date -> allProgress.stream()
                        .filter(p -> p.getDate().equals(date) && p.isCompleted())
                        .findFirst()
                        .isPresent(),
                (v1, v2) -> v1, // merge function in case of duplicates
                LinkedHashMap::new
            ));
        
        return ProgressDashboardResponse.MemberProgressResponse.builder()
                .userEmail(member.getUser().getEmail())
                .userFullName(getUserFullName(member.getUser()))
                .completedDays((int) completedDays)
                .completionPercentage(completionPercentage)
                .dailyStatus(dailyStatus)
                .build();
    }
    
    // Cần thêm mối quan hệ @OneToMany từ PlanMember đến DailyProgress
    // Trong file PlanMember.java:
    // @OneToMany(mappedBy = "planMember", cascade = CascadeType.ALL, orphanRemoval = true)
    // private List<DailyProgress> dailyProgressList;

    // --- Helper Methods ---
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    private Plan findPlanByShareableLink(String link) {
        return planRepository.findByShareableLink(link)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }
    
    private String getUserFullName(User user) {
        if (user.getCustomer() != null) {
            return user.getCustomer().getFullname();
        }
        if (user.getEmployee() != null) {
            return user.getEmployee().getFullname();
        }
        return user.getEmail();
    }
}