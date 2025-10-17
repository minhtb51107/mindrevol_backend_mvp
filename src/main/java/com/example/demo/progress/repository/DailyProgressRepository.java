package com.example.demo.progress.repository;

import com.example.demo.progress.entity.DailyProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyProgressRepository extends JpaRepository<DailyProgress, Long> {
    Optional<DailyProgress> findByPlanMemberIdAndDate(Integer planMemberId, LocalDate date);
    
    @Query("SELECT dp FROM DailyProgress dp " +
            "LEFT JOIN FETCH dp.comments c " +
            "LEFT JOIN FETCH c.author " +
            "LEFT JOIN FETCH dp.reactions r " +
            "LEFT JOIN FETCH r.user " +
            "WHERE dp.id = :id")
     Optional<DailyProgress> findByIdWithDetails(@Param("id") Long id);
}