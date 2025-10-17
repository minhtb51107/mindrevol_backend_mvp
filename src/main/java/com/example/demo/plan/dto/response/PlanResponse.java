package com.example.demo.plan.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class PlanResponse {
    private Integer id;
    private String title;
    private String description;
    private int durationInDays;
    private String dailyGoal;
    private String shareableLink;
    private String creatorEmail;
    private OffsetDateTime createdAt;
    private List<String> memberEmails;
}