package com.example.demo.progress.repository;

import com.example.demo.progress.entity.checkin.CheckInEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set; // Import Set nếu muốn dùng trong tên phương thức, nhưng không bắt buộc

@Repository
public interface CheckInEventRepository extends JpaRepository<CheckInEvent, Long> {

    /**
     * Lấy tất cả các sự kiện check-in cho một plan trong một khoảng thời gian,
     * fetch kèm theo thông tin thành viên, task, attachment để tránh N+1 query.
     * Dùng Set thay vì List trong JOIN FETCH để tránh MultipleBagFetchException.
     * Sử dụng LEFT JOIN FETCH để đảm bảo trả về CheckInEvent ngay cả khi không có attachment hoặc task.
     * Sử dụng DISTINCT để đảm bảo mỗi CheckInEvent chỉ trả về một lần.
     */
    @Query("SELECT DISTINCT cie FROM CheckInEvent cie " +
           "LEFT JOIN FETCH cie.planMember pm " +
           "LEFT JOIN FETCH pm.user " + // Fetch user từ PlanMember
           "LEFT JOIN FETCH cie.attachments att " + // Fetch attachments (giờ là Set)
           "LEFT JOIN FETCH cie.completedTasks ct " + // Fetch completedTasks (giờ là Set)
           "LEFT JOIN FETCH ct.task " + // Fetch Task từ CheckInTask
           "WHERE pm.plan.id = :planId AND cie.checkInTimestamp BETWEEN :start AND :end " +
           "ORDER BY cie.checkInTimestamp ASC") // Sắp xếp kết quả cuối cùng
    List<CheckInEvent> findByPlanIdAndTimestampBetweenWithDetails(
            @Param("planId") Long planId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Có thể thêm các query khác nếu cần sau này
}