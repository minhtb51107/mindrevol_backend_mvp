package com.example.demo.plan.repository;

import com.example.demo.plan.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // --- THÊM IMPORT ---
import org.springframework.data.repository.query.Param; // --- THÊM IMPORT ---
import org.springframework.stereotype.Repository;

import java.time.LocalDate; // --- THÊM IMPORT ---
import java.util.List; // --- THÊM IMPORT ---
import java.util.Optional; // --- THÊM IMPORT ---

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // --- THÊM CÁC PHƯƠNG THỨC NÀY ---

    /**
     * Lấy tất cả các task của một Plan trong một ngày cụ thể, sắp xếp theo thứ tự.
     */
    List<Task> findAllByPlanIdAndTaskDateOrderByOrderAsc(Long planId, LocalDate taskDate);

    /**
     * Lấy tất cả các task của một Plan trong một ngày cụ thể (không cần sắp xếp).
     */
    List<Task> findAllByPlanIdAndTaskDate(Long planId, LocalDate taskDate);


    /**
     * Tìm giá trị 'order' lớn nhất của các task trong một ngày cụ thể của Plan.
     */
    @Query("SELECT MAX(t.order) FROM Task t WHERE t.plan.id = :planId AND t.taskDate = :taskDate")
    Optional<Integer> findMaxOrderByPlanIdAndTaskDate(@Param("planId") Long planId, @Param("taskDate") LocalDate taskDate);
    
    // --- KẾT THÚC THÊM ---
}