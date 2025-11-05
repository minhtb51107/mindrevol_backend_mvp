package com.example.demo.plan.repository;

import com.example.demo.plan.entity.Plan;
import com.example.demo.plan.entity.PlanStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// THÊM CÁC IMPORT NÀY
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Integer> {
    
    // Phương thức này sẽ BỊ ẢNH HƯỞNG bởi @Where (sẽ không tìm thấy plan 'ARCHIVED')
    Optional<Plan> findByShareableLink(String shareableLink);

    // THÊM PHƯƠNG THỨC NÀY
    // Phương thức này "vượt rào" @Where, tìm plan bất kể trạng thái
    @Query("SELECT p FROM Plan p WHERE p.shareableLink = :shareableLink")
    Optional<Plan> findRegardlessOfStatusByShareableLink(@Param("shareableLink") String shareableLink);

	List<Plan> findByStatus(PlanStatus active);
}