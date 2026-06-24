package com.tripcraft.plan.dto;

import java.time.LocalDateTime;

public record TripEvent(
        String type,           // BLOCK_ADDED | BLOCK_MOVED | BLOCK_DELETED |
                               // CANDIDATE_ADDED | CANDIDATE_REMOVED | TRANSIT_RECALCULATED
        Long actorId,
        String actorNickname,
        Object payload,
        LocalDateTime timestamp,
        Long seq               // 일정별 단조 증가 시퀀스(편집 토픽). null=미부여(presence 등)
) {
    public static TripEvent of(String type, Long actorId, String actorNickname, Object payload) {
        return new TripEvent(type, actorId, actorNickname, payload, LocalDateTime.now(), null);
    }

    /** 시퀀스를 부여한 복제본 — broadcast 직전 일정별 카운터로 스탬프한다. */
    public TripEvent withSeq(long seq) {
        return new TripEvent(type, actorId, actorNickname, payload, timestamp, seq);
    }
}
