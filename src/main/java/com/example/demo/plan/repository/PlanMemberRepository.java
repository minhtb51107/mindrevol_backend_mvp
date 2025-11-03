package com.example.demo.plan.repository;

import com.example.demo.plan.entity.PlanMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanMemberRepository extends JpaRepository<PlanMember, Integer> {

    // (Các phương thức cũ của bạn)
    Optional<PlanMember> findByPlanIdAndUserId(Integer planId, Integer userId);

    @Query("SELECT pm FROM PlanMember pm JOIN FETCH pm.plan p WHERE pm.user.id = :userId AND p.status <> 'ARCHIVED'")
    List<PlanMember> findByUserIdWithPlan(@Param("userId") Integer userId);
    
    // --- THÊM 2 PHƯƠNG THỨC MỚI BÊN DƯỚI ---

    /**
     * Tìm PlanMember bằng shareableLink của Plan và ID của User.
     * Dùng cho @PreAuthorize
     */
    @Query("SELECT pm FROM PlanMember pm JOIN pm.plan p WHERE p.shareableLink = :shareableLink AND pm.user.id = :userId")
    Optional<PlanMember> findByPlanShareableLinkAndUserId(@Param("shareableLink") String shareableLink, @Param("userId") Integer userId);

    /**
     * Kiểm tra (true/false) xem User có phải là thành viên của Plan không.
     * Dùng cho @PreAuthorize (nhanh hơn)
     */
    @Query("SELECT COUNT(pm) > 0 FROM PlanMember pm JOIN pm.plan p WHERE p.shareableLink = :shareableLink AND pm.user.id = :userId")
    boolean existsByPlanShareableLinkAndUserId(@Param("shareableLink") String shareableLink, @Param("userId") Integer userId);
}