package com.tripcraft.plan.controller;

import com.tripcraft.plan.dto.TripEvent;
import com.tripcraft.plan.mapper.TripCollaboratorMapper;
import com.tripcraft.plan.mapper.TripMapper;
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
    private final TripMapper tripMapper;
    private final TripCollaboratorMapper collaboratorMapper;

    // tripId → sessionId → PresenceState
    private final Map<Long, Map<String, PresenceState>> presenceMap = new ConcurrentHashMap<>();
    // tripId → blockId → memberId (grab 소유자)
    private final Map<Long, Map<Long, Long>> grabMap = new ConcurrentHashMap<>();
    // sessionId → memberId
    private final Map<String, Long> sessionMemberMap = new ConcurrentHashMap<>();
    // sessionId → tripId (어느 trip을 구독 중인지)
    private final Map<String, Long> sessionTripMap = new ConcurrentHashMap<>();

    private static final long STALE_MILLIS = 5_000;

    record PresenceState(Long memberId, String nickname, double x, double y,
                         String interaction, Long targetBlockId, Instant lastSeen,
                         int snapDayIndex, double snapTop, double cursorRelY,
                         double grabOffsetX, double grabOffsetY) {}

    @MessageMapping("/trip/{tripId}/pointer")
    public void handlePointer(@DestinationVariable Long tripId,
                              Map<String, Object> payload,
                              Principal principal,
                              SimpMessageHeaderAccessor headerAccessor) {
        Long memberId = memberIdFrom(principal, headerAccessor);
        if (memberId == null) return;

        // 접근 권한 확인
        boolean allowed = tripMapper.findById(tripId)
                .map(t -> t.getMemberId().equals(memberId)
                        || collaboratorMapper.findByTripAndMember(tripId, memberId).isPresent())
                .orElse(false);
        if (!allowed) return;

        String sessionId = headerAccessor.getSessionId();
        sessionMemberMap.put(sessionId, memberId);
        sessionTripMap.put(sessionId, tripId);

        double x = toDouble(payload.get("x"));
        double y = toDouble(payload.get("y"));
        String interaction = (String) payload.getOrDefault("interaction", "");
        Long targetBlockId = payload.get("targetBlockId") instanceof Number n
                ? n.longValue() : null;
        String nickname = (String) payload.getOrDefault("nickname", "");
        int snapDayIndex  = payload.get("snapDayIndex")  instanceof Number n ? n.intValue()    : -1;
        double snapTop    = payload.get("snapTop")       instanceof Number n ? n.doubleValue() : -1;
        double cursorRelY  = payload.get("cursorRelY")   instanceof Number n ? n.doubleValue() : y;
        double grabOffsetX = payload.get("grabOffsetX") instanceof Number n ? n.doubleValue() : 0;
        double grabOffsetY = payload.get("grabOffsetY") instanceof Number n ? n.doubleValue() : 0;

        PresenceState state = new PresenceState(memberId, nickname, x, y,
                interaction, targetBlockId, Instant.now(), snapDayIndex, snapTop, cursorRelY,
                grabOffsetX, grabOffsetY);
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
                    m.put("x",           s.x());
                    m.put("y",           s.y());
                    m.put("interaction", s.interaction());
                    m.put("targetBlockId", s.targetBlockId() != null ? s.targetBlockId() : 0L);
                    m.put("snapDayIndex", (long) s.snapDayIndex());
                    m.put("snapTop",      s.snapTop());
                    m.put("cursorRelY",   s.cursorRelY());
                    m.put("grabOffsetX",  s.grabOffsetX());
                    m.put("grabOffsetY",  s.grabOffsetY());
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

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
