package com.example.demo.plan.repository;

import com.example.demo.plan.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    // Có thể thêm các phương thức truy vấn tùy chỉnh nếu cần
}