package com.example.demo.community.repository;

import com.example.demo.community.entity.ProgressReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProgressReactionRepository extends JpaRepository<ProgressReaction, Long> {
    Optional<ProgressReaction> findByDailyProgressIdAndUserId(Long dailyProgressId, Integer userId);
}