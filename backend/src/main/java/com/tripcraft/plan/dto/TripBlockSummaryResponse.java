package com.tripcraft.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class TripBlockSummaryResponse {

    private List<DaySummary> days;

    @Getter
    @AllArgsConstructor
    public static class DaySummary {
        private LocalDate date;
        private List<BlockItem> blocks;
    }

    /** API 응답용 — tripDate 미포함 */
    @Getter
    @AllArgsConstructor
    public static class BlockItem {
        private String attractionName;
        private String startTime;
        private Integer durationMinutes;
    }

    /** 매퍼 쿼리 결과 매핑용 내부 DTO */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class BlockRow {
        private LocalDate tripDate;
        private String attractionName;
        private String startTime;
        private Integer durationMinutes;
    }
}
