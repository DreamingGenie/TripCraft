package com.tripcraft.attraction.service;

import com.tripcraft.attraction.client.TourApiClient;
import com.tripcraft.attraction.client.dto.TourApiItem;
import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.mapper.AttractionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiSyncServiceImpl implements TourApiSyncService {

    private final TourApiClient tourApiClient;
    private final AttractionMapper attractionMapper;

    private static final int[] CONTENT_TYPES = { 12, 14, 28, 32, 38, 39 };
    private static final int[] AREA_CODES    = { 1, 2, 3, 4, 5, 6, 7, 8, 31, 32, 33, 34, 35, 36, 37, 38, 39 };
    private static final int   PAGE_SIZE     = 1000;
    private static final int   BATCH_SIZE    = 500;

    // TourAPI 날짜: "20010307" 또는 "20010307120000"
    private static final DateTimeFormatter DT_FORMATTER = new DateTimeFormatterBuilder()
        .appendPattern("yyyyMMdd")
        .optionalStart().appendPattern("HHmmss").optionalEnd()
        .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .toFormatter();

    @Override
    public SyncResult syncAll() {
        long start = System.currentTimeMillis();
        int total = 0;
        for (int areaCode : AREA_CODES) {
            for (int contentTypeId : CONTENT_TYPES) {
                total += fetchAndSave(areaCode, contentTypeId);
            }
        }
        return new SyncResult(total, System.currentTimeMillis() - start);
    }

    @Override
    public SyncResult syncByArea(int areaCode, int contentTypeId) {
        long start = System.currentTimeMillis();
        int total = fetchAndSave(areaCode, contentTypeId);
        return new SyncResult(total, System.currentTimeMillis() - start);
    }

    private int fetchAndSave(int areaCode, int contentTypeId) {
        int pageNo = 1;
        int saved = 0;
        while (true) {
            List<TourApiItem> items = tourApiClient.fetchAreaList(areaCode, contentTypeId, pageNo, PAGE_SIZE);
            if (items.isEmpty()) break;

            List<Attraction> batch = new ArrayList<>(items.size());
            for (TourApiItem item : items) {
                try {
                    batch.add(toAttraction(item, areaCode));
                } catch (Exception e) {
                    log.warn("관광지 변환 실패 contentid={}: {}", item.getContentid(), e.getMessage());
                }
            }

            // 대용량 배치는 BATCH_SIZE 단위로 나눠서 INSERT
            for (int i = 0; i < batch.size(); i += BATCH_SIZE) {
                List<Attraction> sub = batch.subList(i, Math.min(i + BATCH_SIZE, batch.size()));
                attractionMapper.insertAll(sub);
                saved += sub.size();
            }

            log.info("TourAPI 수집 areaCode={} contentType={} page={} → {}건 저장", areaCode, contentTypeId, pageNo, batch.size());
            if (items.size() < PAGE_SIZE) break;
            pageNo++;
        }
        return saved;
    }

    private Attraction toAttraction(TourApiItem item, int areaCode) {
        Attraction a = new Attraction();
        a.setContentId(item.getContentid());
        a.setContentTypeId(parseIntSafe(item.getContenttypeid()));
        a.setTitle(item.getTitle());
        a.setSidoCode(areaCode);
        a.setSigunguCode(parseIntSafe(item.getSigungucode()));
        a.setAddr1(item.getAddr1());
        a.setAddr2(item.getAddr2());
        // TourAPI: mapx=경도(longitude), mapy=위도(latitude)
        a.setLongitude(parseBigDecimal(item.getMapx()));
        a.setLatitude(parseBigDecimal(item.getMapy()));
        a.setTel(item.getTel());
        a.setFirstImage(item.getFirstimage());
        a.setApiCreatedAt(parseDateTime(item.getCreatedtime()));
        a.setApiModifiedAt(parseDateTime(item.getModifiedtime()));
        return a;
    }

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
