package com.example.demo.feed.repository;

import com.example.demo.feed.entity.FeedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedEventRepository extends JpaRepository<FeedEvent, Long> {

    // Lấy feed cho các plan mà một user cụ thể tham gia
    // JOIN FETCH để lấy thông tin actor và plan (nếu có) trong 1 query
    @Query("SELECT fe FROM FeedEvent fe " +
           "LEFT JOIN FETCH fe.actor a " + // Fetch người thực hiện
           "LEFT JOIN FETCH fe.plan p " +   // Fetch kế hoạch
           "WHERE fe.plan.id IN (SELECT pm.plan.id FROM PlanMember pm WHERE pm.user.id = :userId) " + // Chỉ lấy event từ plan user tham gia
           "ORDER BY fe.timestamp DESC")
    Page<FeedEvent> findFeedForUserPlans(@Param("userId") Integer userId, Pageable pageable);

    // Có thể thêm các query khác nếu cần, ví dụ: lấy feed chỉ cho 1 plan cụ thể
    // Page<FeedEvent> findByPlanIdOrderByTimestampDesc(Integer planId, Pageable pageable);
}