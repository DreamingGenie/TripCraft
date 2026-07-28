package com.tripcraft.attraction.client;

import com.tripcraft.common.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 카카오 로컬 REST API 일일 호출 쿼터 리미터.
 *
 * <p>{@link TourApiCallLimiter}와 동일 구조이나 {@code system_config} 키를 분리해
 * TourAPI 쿼터와 독립적으로 카운트한다. 카카오 로컬 검색 API 무료 쿼터(일 100,000건)에 맞춰
 * {@code kakao_local_daily_limit}로 상한을 조정할 수 있다(기본 100,000).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoLocalCallLimiter {

    private final SystemConfigMapper systemConfigMapper;

    private static final String KEY_DATE  = "kakao_local_call_date";
    private static final String KEY_COUNT = "kakao_local_call_count";
    private static final String KEY_LIMIT = "kakao_local_daily_limit";
    private static final int DEFAULT_LIMIT = 100_000;

    public synchronized boolean tryConsume() {
        String today     = LocalDate.now().toString();
        String savedDate = systemConfigMapper.findValue(KEY_DATE);
        int    limit     = getLimit();

        if (!today.equals(savedDate)) {
            systemConfigMapper.upsert(KEY_DATE,  today);
            systemConfigMapper.upsert(KEY_COUNT, "0");
        }

        String countStr = systemConfigMapper.findValue(KEY_COUNT);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;

        if (count >= limit) {
            log.warn("카카오 로컬 API 일일 호출 한도 도달 ({}/{})", count, limit);
            return false;
        }

        systemConfigMapper.upsert(KEY_COUNT, String.valueOf(count + 1));
        return true;
    }

    public int remainingToday() {
        String today     = LocalDate.now().toString();
        String savedDate = systemConfigMapper.findValue(KEY_DATE);
        if (!today.equals(savedDate)) return getLimit();
        String countStr = systemConfigMapper.findValue(KEY_COUNT);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        return Math.max(0, getLimit() - count);
    }

    private int getLimit() {
        String s = systemConfigMapper.findValue(KEY_LIMIT);
        return s != null ? Integer.parseInt(s) : DEFAULT_LIMIT;
    }
}
