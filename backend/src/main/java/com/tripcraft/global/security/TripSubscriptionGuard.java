package com.tripcraft.global.security;

import com.tripcraft.plan.mapper.TripCollaboratorMapper;
import com.tripcraft.plan.mapper.TripMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 일정 실시간 채널의 접근 판정과, 이미 맺은 구독의 사후 차단을 맡는다.
 *
 * <p>구독 시점 검사만으로는 부족하다 — 구독은 한 번 통과하면 연결이 끊길 때까지 메시지를 받는다.
 * 그래서 누가 어떤 자격(회원 id·공유 토큰)으로 구독했는지 기억해 두었다가, 권한이 바뀌면
 * 다시 판정해 자격을 잃은 세션을 서버가 끊는다.
 */
@Slf4j
@Component
public class TripSubscriptionGuard {

    /** 구독 당시의 자격. 익명이면 memberId 가 null, 링크로 들어오지 않았으면 shareToken 이 null. */
    private record Subscriber(Long memberId, String shareToken) {}

    private final TripMapper tripMapper;
    private final TripCollaboratorMapper collaboratorMapper;
    private final MessageChannel clientOutboundChannel;

    /** tripId → (sessionId → 자격) */
    private final Map<Long, Map<String, Subscriber>> subscribers = new ConcurrentHashMap<>();

    public TripSubscriptionGuard(TripMapper tripMapper,
                                 TripCollaboratorMapper collaboratorMapper,
                                 @Lazy @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.tripMapper = tripMapper;
        this.collaboratorMapper = collaboratorMapper;
        this.clientOutboundChannel = clientOutboundChannel;
    }

    /**
     * 소유자·협업자는 통과. 그 외(익명 포함)는 일정이 공유 상태(비PRIVATE)이고 제시한 공유 토큰이
     * 일정의 현재 토큰과 같을 때만 통과 — 일정 id 만 알아서는 실시간 채널을 볼 수 없다.
     */
    public boolean canAccess(Long tripId, Long memberId, String shareToken) {
        return tripMapper.findById(tripId).map(trip -> {
            if (memberId != null && (trip.getMemberId().equals(memberId)
                    || collaboratorMapper.findByTripAndMember(tripId, memberId).isPresent())) return true;
            if (trip.getShareAccess() == null || "PRIVATE".equals(trip.getShareAccess())) return false;
            return trip.getShareToken() != null && shareToken != null
                    && MessageDigest.isEqual(trip.getShareToken().getBytes(StandardCharsets.UTF_8),
                                             shareToken.getBytes(StandardCharsets.UTF_8));
        }).orElse(false);
    }

    /** 구독이 허용된 세션을 기억한다. */
    public void register(Long tripId, String sessionId, Long memberId, String shareToken) {
        if (sessionId == null) return;
        subscribers.computeIfAbsent(tripId, k -> new ConcurrentHashMap<>())
                .put(sessionId, new Subscriber(memberId, shareToken));
    }

    /** 이 일정을 구독 중인 것으로 기억된 세션 수. */
    public int subscriberCount(Long tripId) {
        Map<String, Subscriber> sessions = subscribers.get(tripId);
        return sessions == null ? 0 : sessions.size();
    }

    /** 연결이 끊긴 세션을 잊는다. */
    public void unregister(String sessionId) {
        if (sessionId == null) return;
        subscribers.values().forEach(m -> m.remove(sessionId));
    }

    /**
     * 권한에 영향을 주는 변경(협업자 제거·공유 설정 변경)이 <b>커밋된 뒤</b> 호출한다.
     * 이 일정을 구독 중인 세션을 다시 판정해, 자격을 잃은 세션에 STOMP ERROR 를 보내 연결을 끊는다
     * (Spring 은 ERROR 프레임을 보낸 뒤 그 WebSocket 세션을 닫는다).
     *
     * @return 끊은 세션 수
     */
    public int revokeStale(Long tripId) {
        Map<String, Subscriber> sessions = subscribers.get(tripId);
        if (sessions == null) return 0;
        int revoked = 0;
        for (Map.Entry<String, Subscriber> e : sessions.entrySet()) {
            Subscriber s = e.getValue();
            if (canAccess(tripId, s.memberId(), s.shareToken())) continue;
            sessions.remove(e.getKey());
            StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
            error.setSessionId(e.getKey());
            error.setMessage("Trip access revoked");
            clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], error.getMessageHeaders()));
            revoked++;
            log.info("협업 WS 구독 차단(권한 상실) tripId={} sessionId={} memberId={}", tripId, e.getKey(), s.memberId());
        }
        return revoked;
    }
}
