package com.tripcraft.attraction.client;

import com.tripcraft.common.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiCallLimiter {

    private final SystemConfigMapper systemConfigMapper;

    private static final String KEY_DATE  = "tour_api_call_date";
    private static final String KEY_COUNT = "tour_api_call_count";
    private static final String KEY_LIMIT = "tour_api_daily_limit";

    public synchronized boolean tryConsume() {
        String today = LocalDate.now().toString();
        String savedDate  = systemConfigMapper.findValue(KEY_DATE);
        String limitStr   = systemConfigMapper.findValue(KEY_LIMIT);
        int    limit      = limitStr != null ? Integer.parseInt(limitStr) : 500;

        if (!today.equals(savedDate)) {
            systemConfigMapper.upsert(KEY_DATE,  today);
            systemConfigMapper.upsert(KEY_COUNT, "0");
            savedDate = today;
        }

        String countStr = systemConfigMapper.findValue(KEY_COUNT);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;

        if (count >= limit) {
            log.warn("TourAPI 일일 호출 한도 도달 ({}/{})", count, limit);
            return false;
        }

        systemConfigMapper.upsert(KEY_COUNT, String.valueOf(count + 1));
        return true;
    }

    public int remainingToday() {
        String today    = LocalDate.now().toString();
        String savedDate = systemConfigMapper.findValue(KEY_DATE);
        if (!today.equals(savedDate)) return getLimit();
        String countStr = systemConfigMapper.findValue(KEY_COUNT);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        return Math.max(0, getLimit() - count);
    }

    private int getLimit() {
        String s = systemConfigMapper.findValue(KEY_LIMIT);
        return s != null ? Integer.parseInt(s) : 500;
    }
}
