package com.example.demo.community.repository;

import com.example.demo.community.entity.ProgressComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgressCommentRepository extends JpaRepository<ProgressComment, Long> {}