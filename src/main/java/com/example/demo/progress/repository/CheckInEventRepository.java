package com.example.demo.progress.repository;

import com.example.demo.plan.entity.PlanMember;
import com.example.demo.progress.entity.checkin.CheckInEvent;
import com.example.demo.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set; 

@Repository
public interface CheckInEventRepository extends JpaRepository<CheckInEvent, Long> {

    /**
     * Lấy tất cả các sự kiện check-in cho một plan trong một khoảng thời gian,
     * fetch kèm theo thông tin thành viên, task, attachment VÀ LINKS để tránh N+1 query.
     */
    @Query("SELECT DISTINCT cie FROM CheckInEvent cie " +
           "LEFT JOIN FETCH cie.planMember pm " +
           "LEFT JOIN FETCH pm.user " + 
           "LEFT JOIN FETCH cie.attachments att " + 
           "LEFT JOIN FETCH cie.completedTasks ct " + 
           "LEFT JOIN FETCH ct.task " +
           "LEFT JOIN FETCH cie.links " + // <-- DÒNG NÀY ĐÃ CÓ (Tốt!)
           "WHERE pm.plan.id = :planId AND cie.checkInTimestamp BETWEEN :start AND :end " +
           "ORDER BY cie.checkInTimestamp ASC") 
    List<CheckInEvent> findByPlanIdAndTimestampBetweenWithDetails(
            @Param("planId") Long planId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    List<CheckInEvent> findAllByPlanMemberIdIn(List<Integer> memberIds);
    
    boolean existsByPlanMemberAndCheckInTimestampAfter(PlanMember planMember, LocalDateTime timestamp);
    
    Optional<CheckInEvent> findFirstByPlanMemberOrderByCheckInTimestampDesc(PlanMember planMember);

	List<CheckInEvent> findByPlanMemberIdAndCheckInTimestampBetween(Integer id, LocalDateTime startOfDay,
			LocalDateTime endOfDay);
	
	// === BẮT ĐẦU SỬA LỖI ===
	
	/**
     * Tìm Logs (CheckInEvents) của một danh sách Tác giả (Users),
     * sắp xếp theo thời gian check-in mới nhất.
     * * SỬA LỖI: Thêm LEFT JOIN FETCH cho tất cả các collection LAZY
     * mà LogResponseDto cần (links, attachments, tasks, reactions, comments)
     * và thêm 'DISTINCT' để tránh bị trùng lặp.
     * Thêm 'countQuery' riêng để Spring Pagination hoạt động chính xác.
     */
    @Query(value = "SELECT DISTINCT cie FROM CheckInEvent cie " +
                   "LEFT JOIN FETCH cie.planMember pm " +
                   "LEFT JOIN FETCH pm.user " +
                   "LEFT JOIN FETCH cie.attachments " +
                   "LEFT JOIN FETCH cie.completedTasks ct " +
                   "LEFT JOIN FETCH ct.task " +
                   "LEFT JOIN FETCH cie.links " +
                   "LEFT JOIN FETCH cie.reactions r " +
                   "LEFT JOIN FETCH r.user " +
                   "LEFT JOIN FETCH cie.comments c " +
                   "LEFT JOIN FETCH c.author " +
                   "WHERE pm.user IN :authors",
           
           countQuery = "SELECT COUNT(DISTINCT cie) FROM CheckInEvent cie " +
                        "JOIN cie.planMember pm " +
                        "WHERE pm.user IN :authors")
    Page<CheckInEvent> findByAuthorsInOrderByCheckInTimestampDesc(
            @Param("authors") List<User> authors, 
            Pageable pageable
    );
    // === KẾT THÚC SỬA LỖI ===
    
 // === SỬA LỖI KHỞI ĐỘNG (FIX LỖI PATH) ===
    @Query("SELECT cie FROM CheckInEvent cie " +
           "WHERE cie.planMember.plan.id = :planId " +
           "AND cie.planMember.user.id = :authorUserId " +
           "ORDER BY cie.checkInTimestamp DESC " +
           "LIMIT 1")
    Optional<CheckInEvent> findTopByPlanIdAndAuthorUserIdOrderByCheckInTimestampDesc(
            @Param("planId") Integer planId, 
            @Param("authorUserId") Integer authorUserId);
    // === KẾT THÚC SỬA LỖI ===
}