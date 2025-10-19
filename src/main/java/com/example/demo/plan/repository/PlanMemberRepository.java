package com.example.demo.plan.repository;

import com.example.demo.plan.entity.PlanMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // Import Query
import org.springframework.data.repository.query.Param; // Import Param
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanMemberRepository extends JpaRepository<PlanMember, Integer> {
    Optional<PlanMember> findByPlanIdAndUserId(Integer planId, Integer userId);

    // --- MODIFY THIS METHOD ---
    // Use @Query with JOIN FETCH to explicitly load the Plan entity
    @Query("SELECT pm FROM PlanMember pm JOIN FETCH pm.plan WHERE pm.user.id = :userId")
    List<PlanMember> findByUserIdWithPlan(@Param("userId") Integer userId);
    // --- END MODIFICATION ---

}