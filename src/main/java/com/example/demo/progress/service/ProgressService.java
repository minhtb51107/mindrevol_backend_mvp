package com.example.demo.progress.service;

import com.example.demo.progress.dto.request.LogProgressRequest;
import com.example.demo.progress.dto.response.DailyProgressResponse;
import com.example.demo.progress.dto.response.ProgressDashboardResponse;

public interface ProgressService {
    DailyProgressResponse logOrUpdateDailyProgress(String shareableLink, String userEmail, LogProgressRequest request);
    ProgressDashboardResponse getProgressDashboard(String shareableLink, String userEmail);
}