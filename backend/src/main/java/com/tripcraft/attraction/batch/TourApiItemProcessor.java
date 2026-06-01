package com.tripcraft.attraction.batch;

import com.tripcraft.attraction.client.dto.TourApiItem;
import com.tripcraft.attraction.domain.Attraction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * TourApiItem → Attraction 변환 Processor.
 *
 * <p>변환 중 예외가 발생한 아이템은 {@code null}을 반환하여 건너뜁니다.
 * Spring Batch는 Processor가 {@code null}을 반환하면 해당 아이템을 Writer에 전달하지 않습니다.
 */
@Slf4j
public class TourApiItemProcessor implements ItemProcessor<TourApiItem, Attraction> {

    // TourAPI 날짜 형식: "20010307" 또는 "20010307120000"
    private static final DateTimeFormatter DT_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMdd")
            .optionalStart().appendPattern("HHmmss").optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();

    /**
     * TourApiItem을 Attraction 도메인 객체로 변환합니다.
     *
     * @param item TourAPI 응답 아이템
     * @return 변환된 Attraction, 변환 실패 시 {@code null} (해당 아이템 Skip)
     */
    @Override
    public Attraction process(TourApiItem item) {
        try {
            Attraction a = new Attraction();
            a.setContentId(item.getContentid());
            a.setContentTypeId(parseIntSafe(item.getContenttypeid()));
            a.setTitle(item.getTitle());
            a.setSidoCode(parseIntSafe(item.getAreacode()));
            a.setSigunguCode(parseIntSafe(item.getSigungucode()));
            a.setAddr1(item.getAddr1());
            a.setAddr2(item.getAddr2());
            // TourAPI: mapx = 경도(longitude), mapy = 위도(latitude)
            a.setLongitude(parseBigDecimal(item.getMapx()));
            a.setLatitude(parseBigDecimal(item.getMapy()));
            a.setTel(item.getTel());
            a.setFirstImage(item.getFirstimage());
            a.setFirstImage2(item.getFirstimage2());
            a.setMlevel(parseIntSafe(item.getMlevel()) == 0 ? null : parseIntSafe(item.getMlevel()));
            a.setZipcode(item.getZipcode());
            a.setCat1(item.getCat1());
            a.setCat2(item.getCat2());
            a.setCat3(item.getCat3());
            a.setLDongRegnCd(item.getLDongRegnCd());
            a.setLDongSignguCd(item.getLDongSignguCd());
            a.setLclsSystm1(item.getLclsSystm1());
            a.setLclsSystm2(item.getLclsSystm2());
            a.setLclsSystm3(item.getLclsSystm3());
            a.setCpyrhtDivCd(item.getCpyrhtDivCd());
            a.setApiCreatedAt(parseDateTime(item.getCreatedtime()));
            a.setApiModifiedAt(parseDateTime(item.getModifiedtime()));
            return a;
        } catch (Exception e) {
            log.warn("관광지 변환 실패 — contentid={}: {}", item.getContentid(), e.getMessage());
            return null; // null 반환 시 해당 아이템 Writer에 전달되지 않음
        }
    }

    // ───────── private ─────────

    private int parseIntSafe(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value.trim(), DT_FORMATTER); }
        catch (Exception e) { return null; }
    }
}
