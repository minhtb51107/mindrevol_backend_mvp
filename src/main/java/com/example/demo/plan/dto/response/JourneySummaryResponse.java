package com.example.demo.plan.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JourneySummaryResponse {
    private String shareableLink;
    private String title;
}