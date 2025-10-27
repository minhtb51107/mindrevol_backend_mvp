package com.example.demo.progress.repository;

import com.example.demo.progress.entity.checkin.CheckInAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckInAttachmentRepository extends JpaRepository<CheckInAttachment, Long> {
}