package com.tripcraft.plan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class TripCopyRequest {

    @NotNull(message = "새 여행 시작일을 입력해주세요.")
    private LocalDate newStartDate;
}
