package com.tripcraft.global.security;

import com.tripcraft.plan.mapper.TripCollaboratorMapper;
import com.tripcraft.plan.mapper.TripMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final TripMapper tripMapper;
    private final TripCollaboratorMapper tripCollaboratorMapper;
    private final TripAccessVersion accessVersion;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == null) return message;

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            case SEND -> handleSend(accessor);
            default -> { /* DISCONNECT 등 */ }
        }
        return message;
    }

    // ── CONNECT: 핸드셰이크에서 쿠키로 저장된 memberId로 Principal 설정 ─────
    // 익명(비로그인)도 연결 허용 — SUBSCRIBE 는 공유(비PRIVATE) 일정만, SEND 는 차단(관전 전용)
    private void handleConnect(StompHeaderAccessor accessor) {
        Long memberId = getMemberIdFromSession(accessor);
        if (memberId != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
        }
    }

    // ── SUBSCRIBE: /topic/trip/{tripId} 구독 시 조회 권한 확인 ──────────────
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/trip/")) return;

        Long tripId = parseTripId(destination, 3);
        if (tripId == null) return;

        // 익명(memberId null)도 공유 일정이면 구독 허용 — canView 에서 share_access 로 판정
        Long memberId = getMemberIdFromSession(accessor);

        // 같은 trip의 두 번째 구독(/presence 등)은 캐시 활용. 단, 접근 권한 세대가 바뀌면 재검증.
        String cacheKey = "tripAccess:" + tripId;
        int gen = accessVersion.current(tripId);
        Object cached = accessor.getSessionAttributes().get(cacheKey);
        if (cached instanceof Integer c && c == gen) return;

        boolean canView = tripMapper.findById(tripId)
                .map(trip -> trip.getMemberId().equals(memberId)
                        || tripCollaboratorMapper.findByTripAndMember(tripId, memberId).isPresent()
                        || (trip.getShareAccess() != null && !"PRIVATE".equals(trip.getShareAccess())))
                .orElse(false);

        if (!canView) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trip access denied");

        accessor.getSessionAttributes().put(cacheKey, gen);
    }

    // ── SEND: /app/trip/{tripId}/... 발행 시 조회 권한 확인 ─────────────────
    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/app/trip/")) return;

        Long tripId = parseTripId(destination, 3);
        if (tripId == null) return;

        Long memberId = getMemberIdFromSession(accessor);
        if (memberId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

        // SUBSCRIBE 캐시와 공유. 접근 권한 세대가 바뀌면(협업자 제거 등) 재검증.
        String cacheKey = "tripAccess:" + tripId;
        int gen = accessVersion.current(tripId);
        Object cached = accessor.getSessionAttributes().get(cacheKey);
        if (cached instanceof Integer c && c == gen) return;

        boolean canView = tripMapper.findById(tripId)
                .map(trip -> trip.getMemberId().equals(memberId)
                        || tripCollaboratorMapper.findByTripAndMember(tripId, memberId).isPresent()
                        || (trip.getShareAccess() != null && !"PRIVATE".equals(trip.getShareAccess())))
                .orElse(false);

        if (!canView) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trip access denied");

        accessor.getSessionAttributes().put(cacheKey, gen);
    }

    // ── helpers ─────────────────────────────────────────────────────────────
    private Long getMemberIdFromSession(StompHeaderAccessor accessor) {
        if (accessor.getSessionAttributes() == null) return null;
        Object id = accessor.getSessionAttributes().get(JwtHandshakeInterceptor.MEMBER_ID_ATTR);
        return id instanceof Long l ? l : null;
    }

    /** destination을 '/'로 분할 후 index 위치의 세그먼트를 Long으로 파싱. 실패 시 null. */
    private Long parseTripId(String destination, int index) {
        String[] parts = destination.split("/");
        if (parts.length <= index) return null;
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
