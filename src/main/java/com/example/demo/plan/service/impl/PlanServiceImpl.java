package com.example.demo.plan.service.impl;

import com.example.demo.plan.dto.request.CreatePlanRequest;
import com.example.demo.plan.dto.request.UpdatePlanRequest;
import com.example.demo.plan.dto.response.PlanDetailResponse;
import com.example.demo.plan.dto.response.PlanPublicResponse;
import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.mapper.PlanMapper;
import com.example.demo.plan.repository.PlanMemberRepository; // Thêm import này
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.plan.service.PlanService;
import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.plan.dto.response.PlanSummaryResponse;
import java.util.List;
import java.util.Objects;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.stream.Collectors; // Thêm import này

@Service
@RequiredArgsConstructor
@Transactional
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanMapper planMapper;
    private final PlanMemberRepository planMemberRepository; // Thêm dependency này

    @Override
    public PlanDetailResponse createPlan(CreatePlanRequest request, String creatorEmail) {
        User creator = findUserByEmail(creatorEmail);

        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Ngày bắt đầu không thể là một ngày trong quá khứ.");
        }

        Plan newPlan = Plan.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .durationInDays(request.getDurationInDays())
                .dailyGoal(request.getDailyGoal())
                .startDate(request.getStartDate())
                .creator(creator)
                .members(new ArrayList<>())
                .dailyTasks(request.getDailyTasks() == null ? new ArrayList<>() : // Thêm dòng này
                            request.getDailyTasks().stream()
                                    .filter(task -> task != null && !task.isBlank()) // Lọc bỏ task rỗng
                                    .collect(Collectors.toList())) // Lưu danh sách tasks
                .build();

        PlanMember creatorAsMember = PlanMember.builder()
                .plan(newPlan)
                .user(creator)
                .role(MemberRole.OWNER)
                .build();
        newPlan.getMembers().add(creatorAsMember);

        Plan savedPlan = planRepository.save(newPlan);
        return planMapper.toPlanDetailResponse(savedPlan);
    }

    @Override
    public PlanDetailResponse joinPlan(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        if (isUserMemberOfPlan(plan, user.getId())) {
            throw new BadRequestException("Bạn đã tham gia kế hoạch này rồi.");
        }

        PlanMember newMember = PlanMember.builder()
                .plan(plan)
                .user(user)
                .role(MemberRole.MEMBER)
                .build();
        plan.getMembers().add(newMember);

        Plan updatedPlan = planRepository.save(plan);
        return planMapper.toPlanDetailResponse(updatedPlan);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getPlanDetails(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        if (isUserMemberOfPlan(plan, user.getId())) {
            return planMapper.toPlanDetailResponse(plan);
        } else {
            return planMapper.toPlanPublicResponse(plan);
        }
    }

    @Override
    public PlanDetailResponse updatePlan(String shareableLink, UpdatePlanRequest request, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        ensureUserIsOwner(plan, user.getId());

        // --- BẮT ĐẦU CẬP NHẬT ---
        plan.setTitle(request.getTitle());
        plan.setDescription(request.getDescription());
        plan.setDurationInDays(request.getDurationInDays());
        plan.setDailyGoal(request.getDailyGoal());
        // Cập nhật dailyTasks: Xóa list cũ và thêm list mới
        plan.getDailyTasks().clear();
        if (request.getDailyTasks() != null) {
             plan.getDailyTasks().addAll(
                 request.getDailyTasks().stream()
                    .filter(task -> task != null && !task.isBlank()) // Lọc bỏ task rỗng
                    .collect(Collectors.toList())
             );
        }
        // --- KẾT THÚC CẬP NHẬT ---

        Plan updatedPlan = planRepository.save(plan);
        return planMapper.toPlanDetailResponse(updatedPlan);
    }

    @Override
    public void leavePlan(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        PlanMember member = plan.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Bạn không phải là thành viên của kế hoạch này."));

        if (member.getRole() == MemberRole.OWNER) {
            throw new BadRequestException("Chủ sở hữu không thể rời khỏi kế hoạch. Bạn cần phải xóa kế hoạch.");
        }

        plan.getMembers().remove(member);
        planRepository.save(plan); // Lưu lại plan sau khi xóa member
    }

    @Override
    public void deletePlan(String shareableLink, String userEmail) {
        Plan plan = findPlanByShareableLink(shareableLink);
        User user = findUserByEmail(userEmail);

        ensureUserIsOwner(plan, user.getId());

        planRepository.delete(plan);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    private Plan findPlanByShareableLink(String link) {
        return planRepository.findByShareableLink(link)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch với link: " + link));
    }

    private boolean isUserMemberOfPlan(Plan plan, Integer userId) {
        // Thêm kiểm tra null cho getMembers()
        return plan.getMembers() != null && plan.getMembers().stream().anyMatch(m -> m.getUser() != null && m.getUser().getId().equals(userId));
    }

    private void ensureUserIsOwner(Plan plan, Integer userId) {
        // Thêm kiểm tra null cho getMembers()
        if (plan.getMembers() == null) {
             throw new AccessDeniedException("Không tìm thấy thông tin thành viên.");
        }
        plan.getMembers().stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(userId) && m.getRole() == MemberRole.OWNER)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Chỉ chủ sở hữu mới có quyền thực hiện hành động này."));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<PlanSummaryResponse> getMyPlans(String userEmail) {
        User user = findUserByEmail(userEmail);
        // Sử dụng phương thức repository đã tạo (hoặc query JPQL nếu bạn chọn cách đó)
        List<PlanMember> planMembers = planMemberRepository.findByUserIdWithPlan(user.getId());

        return planMembers.stream()
                .map(planMapper::toPlanSummaryResponse) // Sử dụng mapper mới
                .filter(Objects::nonNull) // Lọc bỏ kết quả null nếu có lỗi mapping
                .collect(Collectors.toList());
    }
}