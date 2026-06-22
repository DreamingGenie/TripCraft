package com.tripcraft.plan.dto;

import java.time.LocalDateTime;

public record TripEvent(
        String type,           // BLOCK_ADDED | BLOCK_MOVED | BLOCK_DELETED |
                               // CANDIDATE_ADDED | CANDIDATE_REMOVED | TRANSIT_RECALCULATED
        Long actorId,
        String actorNickname,
        Object payload,
        LocalDateTime timestamp
) {
    public static TripEvent of(String type, Long actorId, String actorNickname, Object payload) {
        return new TripEvent(type, actorId, actorNickname, payload, LocalDateTime.now());
    }
}
