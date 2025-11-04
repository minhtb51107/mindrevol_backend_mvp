package com.example.demo.progress.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.progress.entity.checkin.CheckInTask;

@Repository
public interface CheckInTaskRepository extends JpaRepository<CheckInTask, Long> {
	@Modifying
    @Query("DELETE FROM CheckInTask cit WHERE cit.task.id IN :taskIds")
    void deleteAllByTaskIdIn(@Param("taskIds") List<Long> taskIds);
}