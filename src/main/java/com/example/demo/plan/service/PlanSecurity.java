package com.example.demo.plan.service; // Hoặc package bảo mật của bạn

import com.example.demo.plan.entity.MemberRole;
import com.example.demo.plan.entity.PlanMember;
import com.example.demo.plan.repository.PlanMemberRepository;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Component này chứa logic kiểm tra quyền sở hữu và thành viên
 * cho các biểu thức @PreAuthorize trong Controller.
 * Tên "planSecurity" được Spring nhận diện là một Bean.
 */
@Component("planSecurity") // Đặt tên cho Bean là "planSecurity"
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanSecurity {

    private final PlanMemberRepository planMemberRepository;
    private final UserRepository userRepository;

    /**
     * Kiểm tra xem user có phải là THÀNH VIÊN (bao gồm cả Owner) của plan hay không.
     */
    public boolean isMember(String shareableLink, String userEmail) {
        User user = findUserByEmail(userEmail);
        // Dùng phương thức mới trong repository để kiểm tra
        return planMemberRepository.existsByPlanShareableLinkAndUserId(shareableLink, user.getId());
    }

    /**
     * Kiểm tra xem user có phải là CHỦ SỞ HỮU (Owner) của plan hay không.
     */
    public boolean isOwner(String shareableLink, String userEmail) {
        User user = findUserByEmail(userEmail);
        Optional<PlanMember> memberOpt = planMemberRepository.findByPlanShareableLinkAndUserId(shareableLink, user.getId());
        
        return memberOpt.map(member -> member.getRole() == MemberRole.OWNER).orElse(false);
    }

    /**
     * Kiểm tra xem user có phải là THÀNH VIÊN (MEMBER) nhưng KHÔNG PHẢI là chủ sở hữu.
     * Dùng cho chức năng "Rời kế hoạch".
     */
    public boolean isMemberAndNotOwner(String shareableLink, String userEmail) {
        User user = findUserByEmail(userEmail);
        Optional<PlanMember> memberOpt = planMemberRepository.findByPlanShareableLinkAndUserId(shareableLink, user.getId());

        return memberOpt.map(member -> member.getRole() == MemberRole.MEMBER).orElse(false);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng với email: " + email));
    }
}