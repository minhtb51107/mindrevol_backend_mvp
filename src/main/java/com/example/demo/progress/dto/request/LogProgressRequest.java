package com.example.demo.progress.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set; // Thêm import
import java.util.HashSet; // Thêm import

@Getter
@Setter
public class LogProgressRequest {

    @NotNull(message = "Ngày không được để trống")
    private LocalDate date;

    @NotNull(message = "Trạng thái hoàn thành không được để trống")
    private Boolean completed;

    private String notes;

    private String evidence;

    private Set<Integer> completedTaskIndices = new HashSet<>(); // Cho phép rỗng
}