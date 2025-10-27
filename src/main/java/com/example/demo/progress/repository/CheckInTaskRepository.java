package com.example.demo.progress.repository;

import com.example.demo.progress.entity.checkin.CheckInTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckInTaskRepository extends JpaRepository<CheckInTask, Long> {
}