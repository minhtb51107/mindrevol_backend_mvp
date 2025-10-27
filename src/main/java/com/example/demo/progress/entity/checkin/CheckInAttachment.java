package com.example.demo.progress.entity.checkin;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "check_in_attachments")
public class CheckInAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_in_event_id", nullable = false)
    private CheckInEvent checkInEvent;

    @Column(nullable = false)
    private String fileUrl;

    @Column(nullable = false)
    private String storedFilename;

    private String originalFilename;
    private String contentType;
    private Long fileSize;
}