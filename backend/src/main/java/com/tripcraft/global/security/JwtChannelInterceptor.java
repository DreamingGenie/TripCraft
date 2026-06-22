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
            default -> { /* SEND, DISCONNECT 등: 인증 세션 이미 보장 */ }
        }
        return message;
    }

    // ── CONNECT: 핸드셰이크에서 쿠키로 저장된 memberId로 Principal 설정 ─────
    private void handleConnect(StompHeaderAccessor accessor) {
        Long memberId = getMemberIdFromSession(accessor);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "WebSocket 인증 실패: 로그인이 필요합니다.");
        }
        Principal principal = new UsernamePasswordAuthenticationToken(memberId, null, List.of());
        accessor.setUser(principal);
    }

    // ── SUBSCRIBE: /topic/trip/{tripId} 구독 시 조회 권한 확인 ──────────────
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/trip/")) return;

        // /topic/trip/{tripId} or /topic/trip/{tripId}/presence
        String[] parts = destination.split("/");
        if (parts.length < 4) return;

        Long tripId;
        try {
            tripId = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            return;
        }

        Long memberId = getMemberIdFromSession(accessor);
        if (memberId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

        boolean canView = tripMapper.findById(tripId)
                .map(trip -> trip.getMemberId().equals(memberId)
                        || tripCollaboratorMapper.findByTripAndMember(tripId, memberId).isPresent())
                .orElse(false);

        if (!canView) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trip access denied");
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────
    private Long getMemberIdFromSession(StompHeaderAccessor accessor) {
        if (accessor.getSessionAttributes() == null) return null;
        Object id = accessor.getSessionAttributes().get(JwtHandshakeInterceptor.MEMBER_ID_ATTR);
        return id instanceof Long l ? l : null;
    }
}
