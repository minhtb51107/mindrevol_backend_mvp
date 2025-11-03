package com.example.demo.plan.service;

import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.plan.repository.PlanRepository;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service("planSecurity")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanSecurity {

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanMemberRepository planMemberRepository; // (Thêm) Dùng repo này cho nhanh

    // --- CÁC HÀM CŨ (BỊ ẢNH HƯỞNG BỞI @Where) ---

    /**
     * (CŨ) Kiểm tra Owner, nhưng sẽ thất bại nếu Plan đã ARCHIVED.
     */
    public boolean isOwner(String shareableLink, String email) {
        Plan plan = planRepository.findByShareableLink(shareableLink).orElse(null);
        return isOwnerCheck(plan, email);
    }

    /**
     * (CŨ) Kiểm tra Member, nhưng sẽ thất bại nếu Plan đã ARCHIVED.
     */
    public boolean isMember(String shareableLink, String email) {
         Plan plan = planRepository.findByShareableLink(shareableLink).orElse(null);
         return isMemberCheck(plan, email);
    }

    // --- CÁC HÀM MỚI (BỎ QUA @Where) ---

    /**
     * (MỚI) Kiểm tra Owner, BỎ QUA trạng thái ARCHIVED.
     */
    public boolean isOwnerRegardlessOfStatus(String shareableLink, String email) {
        // Dùng phương thức repository bỏ qua @Where
        Plan plan = planRepository.findRegardlessOfStatusByShareableLink(shareableLink).orElse(null);
        return isOwnerCheck(plan, email);
    }

    /**
     * (MỚI) Kiểm tra Member, BỎ QUA trạng thái ARCHIVED.
     * Dùng cho các hành động như "Rời kế hoạch" (Leave).
     */
    public boolean isMemberRegardlessOfStatus(String shareableLink, String email) {
         Plan plan = planRepository.findRegardlessOfStatusByShareableLink(shareableLink).orElse(null);
         return isMemberCheck(plan, email);
    }

    /**
     * (MỚI) Kiểm tra Member và KHÔNG PHẢI Owner, BỎ QUA trạng thái ARCHIVED.
     * Dùng cho "Rời kế hoạch".
     */
    public boolean isMemberAndNotOwner(String shareableLink, String email) {
        Plan plan = planRepository.findRegardlessOfStatusByShareableLink(shareableLink).orElse(null);
        if (plan == null) {
            return false;
        }
        User user = findUserByEmail(email);
        
        return plan.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()) && m.getRole() != MemberRole.OWNER);
    }


    // --- HÀM HELPER (TÁI SỬ DỤNG) ---

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    private boolean isOwnerCheck(Plan plan, String email) {
        if (plan == null) {
            log.warn("[PlanSecurity] isOwnerCheck failed: Plan not found.");
            return false; // Plan not found, deny access
        }
        User user = findUserByEmail(email);
        
        return plan.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()) && m.getRole() == MemberRole.OWNER);
    }

    private boolean isMemberCheck(Plan plan, String email) {
        if (plan == null) {
             log.warn("[PlanSecurity] isMemberCheck failed: Plan not found.");
            return false;
        }
        User user = findUserByEmail(email);
        
        // Tối ưu: Dùng PlanMemberRepository để check nhanh hơn
        return planMemberRepository.existsByPlanShareableLinkAndUserId(plan.getShareableLink(), user.getId());
    }
}