package com.example.demo.community.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.community.entity.ProgressComment;

@Repository
public interface ProgressCommentRepository extends JpaRepository<ProgressComment, Long> {
	@Modifying
    @Query("DELETE FROM ProgressComment pc WHERE pc.dailyProgress.id IN :progressIds")
    void deleteAllByDailyProgressIdIn(@Param("progressIds") List<Long> progressIds);
}