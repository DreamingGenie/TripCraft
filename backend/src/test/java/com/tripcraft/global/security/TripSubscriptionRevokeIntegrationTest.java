package com.tripcraft.global.security;

import com.tripcraft.plan.service.TripService;
import com.tripcraft.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 실제 WebSocket 연결로 본다: 권한을 잃으면 이미 맺은 구독이 서버 쪽에서 끊기는가.
 * (구독 시점 검사는 단위테스트 몫. 여기서는 "한 번 통과한 구독이 계속 살아 있는" 구멍만 다룬다.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripSubscriptionRevokeIntegrationTest extends AbstractIntegrationTest {

    static final AtomicInteger SEQ = new AtomicInteger();

    @LocalServerPort int port;
    @Autowired TripService tripService;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;

    WebSocketStompClient stomp;
    long ownerId;
    long collaboratorId;
    long tripId;

    @BeforeEach
    void setUp() {
        ownerId = member("owner");
        collaboratorId = member("collab");
        jdbc.update("INSERT INTO trip (member_id, title, start_date, end_date) VALUES (?, 'revoke-it', '2026-07-01', '2026-07-03')",
                ownerId);
        tripId = jdbc.queryForObject("SELECT MAX(id) FROM trip WHERE member_id = ?", Long.class, ownerId);
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new StringMessageConverter());
    }

    @AfterEach
    void tearDown() {
        stomp.stop();
    }

    /** 깨지면: 공유를 끈 뒤에도 링크로 들어와 있던 익명 관전자가 편집 이벤트를 계속 받는다. */
    @Test
    void 공유를_끄면_링크로_구독하던_익명_세션이_끊긴다() throws Exception {
        String token = tripService.setShareAccess(tripId, "VIEW", ownerId);
        StompSession viewer = connect(null);
        subscribe(viewer, token);
        StompSession owner = connect(ownerId);
        subscribe(owner, null);
        awaitSubscribed(viewer, owner);

        tripService.setShareAccess(tripId, "PRIVATE", ownerId);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(viewer.isConnected()).isFalse());
        assertThat(owner.isConnected()).isTrue();   // 자격이 남은 세션은 건드리지 않는다
    }

    /** 깨지면: 협업자에서 뺀 사람이 열어 둔 연결로 편집 이벤트를 계속 받는다. */
    @Test
    void 협업자에서_빼면_그_사람의_구독_세션이_끊긴다() throws Exception {
        tripService.inviteCollaborator(tripId, collaboratorId, "EDITOR", ownerId);
        StompSession collaborator = connect(collaboratorId);
        subscribe(collaborator, null);
        StompSession owner = connect(ownerId);
        subscribe(owner, null);
        awaitSubscribed(collaborator, owner);

        tripService.removeCollaborator(tripId, collaboratorId, ownerId);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(collaborator.isConnected()).isFalse());
        assertThat(owner.isConnected()).isTrue();
    }

    /** memberId 가 있으면 로그인 쿠키를 실어, 없으면 익명으로 연결한다. */
    private StompSession connect(Long memberId) throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        if (memberId != null) {
            handshake.add("Cookie", "access_token=" + tokens.createAccessToken(memberId, "USER"));
        }
        return stomp.connectAsync("ws://localhost:" + port + "/ws/websocket", handshake,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private void subscribe(StompSession session, String shareToken) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/topic/trip/" + tripId);
        if (shareToken != null) headers.set(JwtChannelInterceptor.SHARE_TOKEN_HEADER, shareToken);
        session.subscribe(headers, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return String.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) { }
        });
    }

    /** SUBSCRIBE 프레임이 서버에서 처리될 때까지 기다린다(처리 전에 권한을 바꾸면 끊을 대상이 없다). */
    private void awaitSubscribed(StompSession... sessions) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            for (StompSession s : sessions) assertThat(s.isConnected()).isTrue();
            assertThat(guardedSessions()).isGreaterThanOrEqualTo(sessions.length);
        });
    }

    @Autowired TripSubscriptionGuard guard;

    private int guardedSessions() {
        return guard.subscriberCount(tripId);
    }

    private long member(String nickname) {
        String email = "revoke-it-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
        jdbc.update("INSERT INTO member (email, nickname) VALUES (?, ?)", email, nickname);
        return jdbc.queryForObject("SELECT id FROM member WHERE email = ?", Long.class, email);
    }
}
