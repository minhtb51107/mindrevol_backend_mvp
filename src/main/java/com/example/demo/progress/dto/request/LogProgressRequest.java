package com.example.demo.progress.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class LogProgressRequest {

    @NotNull(message = "Ngày không được để trống")
    private LocalDate date;

    @NotNull(message = "Trạng thái hoàn thành không được để trống")
    private Boolean completed;

    private String notes;

    private String evidence;
}