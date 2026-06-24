package com.tripcraft.global.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 일정별 "접근 권한 세대(generation)" 카운터.
 *
 * STOMP 권한 검증은 비용 절감을 위해 SUBSCRIBE/SEND 시 1회 검증 후 세션에 캐시한다.
 * 협업자 제거·역할 변경·공유 설정 변경이 일어나면 해당 일정의 세대를 올려, 캐시된 세대와
 * 어긋나는 세션이 다음 프레임에서 권한을 재검증하도록 만든다(즉시 무효화).
 */
@Component
public class TripAccessVersion {

    private final ConcurrentHashMap<Long, AtomicInteger> gen = new ConcurrentHashMap<>();

    /** 현재 세대. 변경이 한 번도 없으면 0. */
    public int current(Long tripId) {
        AtomicInteger v = gen.get(tripId);
        return v != null ? v.get() : 0;
    }

    /** 접근 권한에 영향을 주는 변경 발생 시 호출 — 캐시 무효화 유도. */
    public void bump(Long tripId) {
        gen.computeIfAbsent(tripId, k -> new AtomicInteger()).incrementAndGet();
    }
}
