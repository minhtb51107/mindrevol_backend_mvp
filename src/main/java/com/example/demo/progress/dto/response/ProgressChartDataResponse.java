package com.example.demo.progress.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProgressChartDataResponse {
    private LocalDate date;         // Ngày
    private double completionRate; // Tỷ lệ hoàn thành (0.0 - 100.0)
}