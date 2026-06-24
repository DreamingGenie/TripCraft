package com.tripcraft.plan.controller;

import com.tripcraft.plan.dto.TripEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TripPresenceController {

    private final SimpMessagingTemplate messaging;

    // tripId → sessionId → PresenceState
    private final Map<Long, Map<String, PresenceState>> presenceMap = new ConcurrentHashMap<>();
    // tripId → blockId → memberId (grab 소유자)
    private final Map<Long, Map<Long, Long>> grabMap = new ConcurrentHashMap<>();
    // sessionId → memberId
    private final Map<String, Long> sessionMemberMap = new ConcurrentHashMap<>();
    // sessionId → tripId (어느 trip을 구독 중인지)
    private final Map<String, Long> sessionTripMap = new ConcurrentHashMap<>();

    private static final long STALE_MILLIS = 5_000;

    // zone 기반 의미 좌표 (절대 픽셀 X). 좌표 의미는 프런트 useCollabCursor.js 참고.
    record PresenceState(Long memberId, String nickname, String zone,
                         String interaction, Long targetBlockId, Instant lastSeen,
                         int dayIndex, double colRatioX, double contentY,
                         double mapRatioX, double mapRatioY,
                         double grabRatioX, double grabOffsetMin) {}

    @MessageMapping("/trip/{tripId}/pointer")
    public void handlePointer(@DestinationVariable Long tripId,
                              Map<String, Object> payload,
                              Principal principal,
                              SimpMessageHeaderAccessor headerAccessor) {
        Long memberId = memberIdFrom(principal, headerAccessor);
        if (memberId == null) return;

        // 권한은 JwtChannelInterceptor.handleSend()가 SEND 프레임 단계에서 이미 검증(+세션 캐시)하므로
        // 여기서 매 포인터마다 DB를 재조회하지 않는다. (커서 throttle·keepalive로 호출 빈도가 높음)

        String sessionId = headerAccessor.getSessionId();
        sessionMemberMap.put(sessionId, memberId);
        sessionTripMap.put(sessionId, tripId);

        String zone = (String) payload.getOrDefault("zone", "other");
        String interaction = (String) payload.getOrDefault("interaction", "");
        Long targetBlockId = payload.get("targetBlockId") instanceof Number n
                ? n.longValue() : null;
        String nickname = (String) payload.getOrDefault("nickname", "");
        int dayIndex       = payload.get("dayIndex")      instanceof Number n ? n.intValue()    : -1;
        double colRatioX   = payload.get("colRatioX")     instanceof Number n ? n.doubleValue() : 0;
        double contentY    = payload.get("contentY")      instanceof Number n ? n.doubleValue() : 0;
        double mapRatioX   = payload.get("mapRatioX")     instanceof Number n ? n.doubleValue() : 0;
        double mapRatioY   = payload.get("mapRatioY")     instanceof Number n ? n.doubleValue() : 0;
        double grabRatioX  = payload.get("grabRatioX")    instanceof Number n ? n.doubleValue() : 0;
        double grabOffsetMin = payload.get("grabOffsetMin") instanceof Number n ? n.doubleValue() : 0;

        PresenceState state = new PresenceState(memberId, nickname, zone,
                interaction, targetBlockId, Instant.now(), dayIndex, colRatioX, contentY,
                mapRatioX, mapRatioY, grabRatioX, grabOffsetMin);
        presenceMap.computeIfAbsent(tripId, k -> new ConcurrentHashMap<>())
                   .put(sessionId, state);

        // grab 처리
        Map<Long, Long> tripGrab = grabMap.computeIfAbsent(tripId, k -> new ConcurrentHashMap<>());
        if ("grab".equals(interaction) && targetBlockId != null) {
            tripGrab.put(targetBlockId, memberId);
        } else if (targetBlockId != null) {
            tripGrab.remove(targetBlockId);
        }

        broadcastPresence(tripId);
    }

    /** grab 소유자인지 확인 — TripServiceImpl에서 사용 */
    public Long getGrabOwner(Long tripId, Long blockId) {
        Map<Long, Long> tripGrab = grabMap.get(tripId);
        return tripGrab != null ? tripGrab.get(blockId) : null;
    }

    @Scheduled(fixedDelay = 2000)
    public void evictStale() {
        Instant cutoff = Instant.now().minusMillis(STALE_MILLIS);
        presenceMap.forEach((tripId, sessions) -> {
            List<String> removed = new ArrayList<>();
            sessions.forEach((sessionId, state) -> {
                if (state.lastSeen().isBefore(cutoff)) {
                    removed.add(sessionId);
                }
            });
            if (!removed.isEmpty()) {
                removed.forEach(sid -> {
                    PresenceState s = sessions.remove(sid);
                    if (s != null) releaseGrab(tripId, s.memberId());
                });
                broadcastPresence(tripId);
            }
        });
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long tripId = sessionTripMap.remove(sessionId);
        Long memberId = sessionMemberMap.remove(sessionId);
        if (tripId != null) {
            Map<String, PresenceState> sessions = presenceMap.get(tripId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (memberId != null) releaseGrab(tripId, memberId);
                broadcastPresence(tripId);
            }
        }
    }

    private void releaseGrab(Long tripId, Long memberId) {
        Map<Long, Long> tripGrab = grabMap.get(tripId);
        if (tripGrab != null) {
            tripGrab.entrySet().removeIf(e -> e.getValue().equals(memberId));
        }
    }

    private void broadcastPresence(Long tripId) {
        Map<String, PresenceState> sessions = presenceMap.getOrDefault(tripId, Map.of());
        List<Map<String, Object>> participants = sessions.values().stream()
                .map(s -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("memberId",     s.memberId());
                    m.put("nickname",     s.nickname());
                    m.put("zone",         s.zone());
                    m.put("interaction",  s.interaction());
                    m.put("targetBlockId", s.targetBlockId() != null ? s.targetBlockId() : 0L);
                    m.put("dayIndex",     (long) s.dayIndex());
                    m.put("colRatioX",    s.colRatioX());
                    m.put("contentY",     s.contentY());
                    m.put("mapRatioX",    s.mapRatioX());
                    m.put("mapRatioY",    s.mapRatioY());
                    m.put("grabRatioX",   s.grabRatioX());
                    m.put("grabOffsetMin", s.grabOffsetMin());
                    return m;
                })
                .toList();
        messaging.convertAndSend("/topic/trip/" + tripId + "/presence",
                TripEvent.of("PRESENCE_UPDATE", null, null,
                        Map.of("participants", participants)));
    }

    private Long memberIdFrom(Principal principal, SimpMessageHeaderAccessor accessor) {
        if (principal != null) {
            try { return Long.parseLong(principal.getName()); } catch (NumberFormatException ignored) {}
        }
        if (accessor.getSessionAttributes() != null) {
            Object id = accessor.getSessionAttributes().get("memberId");
            if (id instanceof Long l) return l;
        }
        return null;
    }
}
