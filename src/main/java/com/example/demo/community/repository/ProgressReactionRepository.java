package com.example.demo.community.repository;

import com.example.demo.community.entity.ProgressReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
// THÊM IMPORT NÀY
import com.example.demo.community.entity.ReactionType; 

@Repository
public interface ProgressReactionRepository extends JpaRepository<ProgressReaction, Long> {
    
    // Phương thức cũ của bạn (vẫn giữ)
    Optional<ProgressReaction> findByCheckInEventIdAndUserId(Long checkInEventId, Integer userId);
    
    // === THÊM PHƯƠNG THỨC MỚI (BẮT BUỘC) ===
    // SỬA LỖI 3: Thêm phương thức này để logic toggle-reaction hoạt động
    Optional<ProgressReaction> findByCheckInEventIdAndUserIdAndType(Long checkInEventId, Integer userId, ReactionType type);
    // === KẾT THÚC THÊM MỚI ===

	void deleteAllByDailyProgressIdIn(List<Long> progressIds);
}