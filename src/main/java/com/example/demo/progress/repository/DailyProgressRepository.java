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

    // Query này giờ đây an toàn vì chỉ có comments là List (Bag), reactions là Set
    @Query("SELECT dp FROM DailyProgress dp " +
           "LEFT JOIN FETCH dp.planMember pm " + // Fetch thêm planMember để tránh N+1 khi lấy plan/user
           "LEFT JOIN FETCH pm.user " +          // Fetch user
           "LEFT JOIN FETCH pm.plan p " +           // Fetch plan
           "LEFT JOIN FETCH dp.comments c " +
           "LEFT JOIN FETCH c.author ca " +       // Fetch author của comment
           "LEFT JOIN FETCH dp.reactions r " +
           "LEFT JOIN FETCH r.user ru " +         // Fetch user của reaction
           "WHERE dp.id = :id")
     Optional<DailyProgress> findByIdWithDetails(@Param("id") Long id);
}