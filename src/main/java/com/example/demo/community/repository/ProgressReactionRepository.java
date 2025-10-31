package com.example.demo.community.repository;

import com.example.demo.community.entity.ProgressReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProgressReactionRepository extends JpaRepository<ProgressReaction, Long> {
    
    // === THAY ĐỔI PHƯƠNG THỨC TRUY VẤN ===
    //
    // TÊN CŨ: 
    // Optional<ProgressReaction> findByDailyProgressIdAndUserId(Long dailyProgressId, Integer userId);
    //
    // TÊN MỚI (và tham số mới):
    Optional<ProgressReaction> findByCheckInEventIdAndUserId(Long checkInEventId, Integer userId);
    //
    // === KẾT THÚC THAY ĐỔI ===
}