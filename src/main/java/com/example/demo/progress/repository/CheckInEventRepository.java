package com.example.demo.progress.repository;

import com.example.demo.plan.entity.PlanMember;
import com.example.demo.progress.entity.checkin.CheckInEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
           "LEFT JOIN FETCH cie.links " + // <-- THÊM MỚI DÒNG NÀY ĐỂ SỬA LỖI "no Session"
           "WHERE pm.plan.id = :planId AND cie.checkInTimestamp BETWEEN :start AND :end " +
           "ORDER BY cie.checkInTimestamp ASC") 
    List<CheckInEvent> findByPlanIdAndTimestampBetweenWithDetails(
            @Param("planId") Long planId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

 // Thêm phương thức này
    List<CheckInEvent> findAllByPlanMemberIdIn(List<Integer> memberIds);
    
    boolean existsByPlanMemberAndCheckInTimestampAfter(PlanMember planMember, LocalDateTime timestamp);
    
    Optional<CheckInEvent> findFirstByPlanMemberOrderByCheckInTimestampDesc(PlanMember planMember);
}