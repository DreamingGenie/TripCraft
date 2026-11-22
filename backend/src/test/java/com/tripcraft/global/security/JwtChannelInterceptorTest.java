package com.tripcraft.global.security;

import com.tripcraft.plan.domain.Trip;
import com.tripcraft.plan.domain.TripCollaborator;
import com.tripcraft.plan.mapper.TripCollaboratorMapper;
import com.tripcraft.plan.mapper.TripMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 협업 채널 구독·발행 권한 판정. 규칙: 소유자·협업자는 통과, 그 외(익명 포함)는
 * 공유 상태 일정에 공유 링크 토큰을 제시해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    static final long TRIP_ID = 10L;
    static final long OWNER_ID = 1L;
    static final long COLLABORATOR_ID = 2L;
    static final long STRANGER_ID = 3L;
    static final String TOKEN = "share-token-abc";

    @Mock TripMapper tripMapper;
    @Mock TripCollaboratorMapper collaboratorMapper;
    @Mock TripAccessVersion accessVersion;

    @Mock org.springframework.messaging.MessageChannel clientOutboundChannel;

    JwtChannelInterceptor interceptor;

    Trip trip;
    TripSubscriptionGuard guard;
    Map<String, Object> session;

    @BeforeEach
    void setUp() {
        trip = new Trip();
        trip.setId(TRIP_ID);
        trip.setMemberId(OWNER_ID);
        trip.setShareAccess("VIEW");
        trip.setShareToken(TOKEN);
        session = new HashMap<>();
        guard = new TripSubscriptionGuard(tripMapper, collaboratorMapper, clientOutboundChannel);
        interceptor = new JwtChannelInterceptor(guard, accessVersion);
        lenient().when(tripMapper.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        lenient().when(collaboratorMapper.findByTripAndMember(TRIP_ID, COLLABORATOR_ID))
                .thenReturn(Optional.of(new TripCollaborator()));
    }

    /** 깨지면: 일정 id 만 아는 익명 사용자가 공유 일정의 실시간 이벤트(메모 내용 포함)를 볼 수 있다. */
    @Test
    void 익명은_토큰_없이_공유_일정을_구독할_수_없다() {
        assertThatThrownBy(() -> subscribe(null, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 익명도_올바른_공유_토큰이면_구독한다() {
        assertThatCode(() -> subscribe(null, TOKEN)).doesNotThrowAnyException();
    }

    @Test
    void 틀린_공유_토큰은_거부한다() {
        assertThatThrownBy(() -> subscribe(null, "wrong")).isInstanceOf(ResponseStatusException.class);
    }

    /** 깨지면: 로그인만 하면 남의 공유 일정을 id 로 구독할 수 있다. */
    @Test
    void 로그인한_제3자도_토큰이_없으면_거부한다() {
        assertThatThrownBy(() -> subscribe(STRANGER_ID, null)).isInstanceOf(ResponseStatusException.class);
    }

    /** 깨지면: 토큰 요구가 기존 소유자·협업자의 협업 연결을 끊는다. */
    @Test
    void 소유자와_협업자는_토큰_없이_구독한다() {
        assertThatCode(() -> subscribe(OWNER_ID, null)).doesNotThrowAnyException();
        session.clear();
        assertThatCode(() -> subscribe(COLLABORATOR_ID, null)).doesNotThrowAnyException();
    }

    /** 깨지면: 공유를 끈 뒤에도 예전 링크 토큰으로 구독된다. */
    @Test
    void 비공개로_돌린_일정은_토큰이_맞아도_거부한다() {
        trip.setShareAccess("PRIVATE");
        assertThatThrownBy(() -> subscribe(null, TOKEN)).isInstanceOf(ResponseStatusException.class);
    }

    /** 깨지면: 권한 세대가 바뀐 뒤 링크 참가자의 발행 재검증이 토큰을 잃어 정상 사용자가 차단된다(또는 재검증이 생략된다). */
    @Test
    void 권한_세대가_바뀌면_발행_때_공유_상태를_다시_본다() {
        subscribe(STRANGER_ID, TOKEN);
        when(accessVersion.current(TRIP_ID)).thenReturn(1);

        assertThatCode(() -> send(STRANGER_ID)).doesNotThrowAnyException();

        when(accessVersion.current(TRIP_ID)).thenReturn(2);
        trip.setShareAccess("PRIVATE");
        assertThatThrownBy(() -> send(STRANGER_ID)).isInstanceOf(ResponseStatusException.class);
    }

    /** 깨지면: 협업자에서 빠진 사람이 열어 둔 연결로 편집 이벤트를 계속 받는다. */
    @Test
    void 권한을_잃은_구독_세션만_끊는다() {
        subscribe("owner-session", OWNER_ID, null);
        subscribe("collab-session", COLLABORATOR_ID, null);
        subscribe("link-session", null, TOKEN);

        when(collaboratorMapper.findByTripAndMember(TRIP_ID, COLLABORATOR_ID)).thenReturn(Optional.empty());
        trip.setShareAccess("PRIVATE");

        assertThat(guard.revokeStale(TRIP_ID)).isEqualTo(2);

        ArgumentCaptor<Message<?>> sent = ArgumentCaptor.forClass(Message.class);
        verify(clientOutboundChannel, times(2)).send(sent.capture());
        assertThat(sent.getAllValues()).allSatisfy(m ->
                assertThat(StompHeaderAccessor.wrap(m).getCommand()).isEqualTo(StompCommand.ERROR));
        assertThat(sent.getAllValues().stream().map(m -> StompHeaderAccessor.wrap(m).getSessionId()))
                .containsExactlyInAnyOrder("collab-session", "link-session");
        assertThat(guard.revokeStale(TRIP_ID)).isZero();   // 이미 끊은 세션을 다시 끊지 않는다
    }

    // SimpleBroker 는 구독 경로를 Ant 패턴으로 맞춘다 — 와일드카드가 통과하면 토큰·id 없이 모든 일정 이벤트를 받는다(2026-11-22 반증에서 실측)
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/topic/**", "/topic/trip/**", "/topic/trip/*", "/topic/trip/*/presence", "/topic/other", "/queue/x", "/topic/trip/abc"})
    void 허용_목록_밖의_구독_경로는_거부한다(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        assertThatThrownBy(() -> preSend(accessor, OWNER_ID)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 참가자_표시_채널도_같은_권한_검사를_거친다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/trip/" + TRIP_ID + "/presence");
        assertThatThrownBy(() -> preSend(accessor, null)).isInstanceOf(ResponseStatusException.class);
    }

    private void subscribe(String sessionId, Long memberId, String shareToken) {
        session = new HashMap<>();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/topic/trip/" + TRIP_ID);
        if (shareToken != null) accessor.setNativeHeader(JwtChannelInterceptor.SHARE_TOKEN_HEADER, shareToken);
        preSend(accessor, memberId);
    }

    private void subscribe(Long memberId, String shareToken) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/trip/" + TRIP_ID);
        if (shareToken != null) accessor.setNativeHeader(JwtChannelInterceptor.SHARE_TOKEN_HEADER, shareToken);
        preSend(accessor, memberId);
    }

    private void send(Long memberId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/trip/" + TRIP_ID + "/presence");
        preSend(accessor, memberId);
    }

    private void preSend(StompHeaderAccessor accessor, Long memberId) {
        if (memberId != null) session.put(JwtHandshakeInterceptor.MEMBER_ID_ATTR, memberId);
        accessor.setSessionAttributes(session);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(message, null);
    }
}
