package com.example.demo.plan.repository;

import com.example.demo.plan.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Integer> {
    Optional<Plan> findByShareableLink(String shareableLink);
}