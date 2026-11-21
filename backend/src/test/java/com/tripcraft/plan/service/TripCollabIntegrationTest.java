package com.tripcraft.plan.service;

import com.tripcraft.plan.dto.BlockCreateRequest;
import com.tripcraft.plan.dto.BlockUpdateRequest;
import com.tripcraft.plan.dto.TransitResponse;
import com.tripcraft.plan.dto.TripEvent;
import com.tripcraft.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실시간 협업 편집 경로 통합테스트 — 실 MySQL + 실제 트랜잭션 프록시.
 *
 * <p>단위테스트(Mockito)는 트랜잭션 밖에서 돌아 ①afterCommit 콜백의 실제 실행 시점 ②InnoDB 락·격리수준
 * ③낙관적 락 SQL 을 검증하지 못한다. 여기서는 그 셋만 다룬다(권한·검증 분기는 단위테스트 몫).
 * 외부 호출인 이동시간 API({@link TransitService})와 STOMP 전송만 목으로 바꾼다.
 */
class TripCollabIntegrationTest extends AbstractIntegrationTest {

    static final LocalDate START = LocalDate.of(2026, 7, 1);
    static final int TRIP_DAYS = 30;
    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired TripService tripService;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean TransitService transitService;
    @MockitoBean SimpMessagingTemplate messaging;

    long ownerId;
    long tripId;
    ExecutorService pool;

    @BeforeEach
    void setUp() {
        ownerId = insert("INSERT INTO member (email, nickname) VALUES (?, ?)",
                "collab-it-" + SEQ.incrementAndGet() + "@test.local", "owner");
        tripId = insert("INSERT INTO trip (member_id, title, start_date, end_date) VALUES (?, ?, ?, ?)",
                ownerId, "통합테스트 일정", START, START.plusDays(TRIP_DAYS - 1));
        when(transitService.getTransitByCoords(any(), any(), any(), any(), anyInt(), anyString()))
                .thenReturn(Optional.of(TransitResponse.builder().durationMinutes(15).transportMode("BUS").build()));
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    // ── afterCommit ──────────────────────────────────────────────────────────

    /** 깨지면: 재계산 완료 알림이 트랜잭션 경로에서 나가지 않는다(afterCommit 안에서 등록한 콜백은 불리지 않음). */
    @Test
    void 블록을_배치하면_재계산_완료_알림까지_나간다() {
        place(candidate(), START, LocalTime.of(9, 0), 60);
        place(candidate(), START, LocalTime.of(11, 0), 60);

        assertThat(awaitEventTypes("TRANSIT_RECALCULATED")).contains("BLOCK_ADDED", "TRANSIT_RECALCULATED");
    }

    /** 깨지면: 시스템 재계산이 version 을 올려 사용자의 다음 편집이 오탐 409 가 된다. */
    @Test
    void 재계산은_이동시간만_쓰고_version은_올리지_않는다() {
        place(candidate(), START, LocalTime.of(9, 0), 60);
        long second = place(candidate(), START, LocalTime.of(11, 0), 60);
        awaitEventTypes("TRANSIT_RECALCULATED");

        awaitTransit(second, 15);
        assertThat(version(second)).isZero();
    }

    /** 깨지면: 외부 이동시간 API 장애가 이미 커밋된 편집을 실패로 보이게 하거나 되돌린다. */
    @Test
    void 재계산이_실패해도_편집은_커밋된_채_남는다() {
        when(transitService.getTransitByCoords(any(), any(), any(), any(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("이동시간 API 장애"));

        place(candidate(), START, LocalTime.of(9, 0), 60);
        long second = place(candidate(), START, LocalTime.of(11, 0), 60);

        assertThat(blockCount(START)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT transit_duration_minutes FROM trip_block WHERE id = ?", Integer.class, second)).isNull();
    }

    // ── 일정 행 FOR UPDATE 직렬화 ────────────────────────────────────────────

    /** 깨지면: 겹치는 시간대의 동시 배치가 둘 다 통과한다(여러 행에 걸친 제약이 직렬화되지 않음). */
    @Test
    void 겹치는_시간대의_동시_배치는_하나만_성공한다() throws Exception {
        for (int round = 0; round < 10; round++) {
            LocalDate date = START.plusDays(round);
            long c1 = candidate(), c2 = candidate();

            List<Boolean> results = race(
                    () -> place(c1, date, LocalTime.of(10, 0), 60),
                    () -> place(c2, date, LocalTime.of(10, 30), 60));

            assertThat(results).as("round %d", round).containsExactlyInAnyOrder(true, false);
            assertThat(blockCount(date)).as("round %d", round).isEqualTo(1);
        }
    }

    /** 깨지면: 동시 배치가 같은 display_order 를 받는다(순서 할당이 최신 커밋 상태를 보지 못함). */
    @Test
    void 겹치지_않는_동시_배치는_서로_다른_순서를_받는다() throws Exception {
        for (int round = 0; round < 10; round++) {
            LocalDate date = START.plusDays(round);
            long c1 = candidate(), c2 = candidate();

            List<Boolean> results = race(
                    () -> place(c1, date, LocalTime.of(9, 0), 60),
                    () -> place(c2, date, LocalTime.of(14, 0), 60));

            assertThat(results).as("round %d", round).containsExactly(true, true);
            assertThat(jdbc.queryForList("""
                    SELECT tb.display_order FROM trip_block tb
                    JOIN trip_candidate tc ON tc.id = tb.candidate_id
                    WHERE tc.trip_id = ? AND tb.trip_date = ? ORDER BY tb.display_order""",
                    Integer.class, tripId, date)).as("round %d", round).containsExactly(1, 2);
        }
    }

    // ── 블록 낙관적 락 ───────────────────────────────────────────────────────

    /** 깨지면: 같은 버전을 본 두 편집이 둘 다 반영된다(나중 편집이 앞선 편집을 말없이 덮어씀). */
    @Test
    void 같은_버전을_본_동시_이동은_하나만_성공한다() throws Exception {
        for (int round = 0; round < 10; round++) {
            LocalDate date = START.plusDays(round);
            long blockId = place(candidate(), date, LocalTime.of(9, 0), 60);

            List<Boolean> results = race(
                    () -> move(blockId, date, LocalTime.of(12, 0), 0),
                    () -> move(blockId, date, LocalTime.of(15, 0), 0));

            assertThat(results).as("round %d", round).containsExactlyInAnyOrder(true, false);
            assertThat(version(blockId)).as("round %d", round).isEqualTo(1);
        }
    }

    /** 깨지면: 이미 바뀐 블록을 옛 버전으로 고쳐도 409 가 나지 않는다. */
    @Test
    void 옛_버전으로_이동하면_409() {
        long blockId = place(candidate(), START, LocalTime.of(9, 0), 60);
        move(blockId, START, LocalTime.of(12, 0), 0);

        assertThatThrownBy(() -> move(blockId, START, LocalTime.of(15, 0), 0))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(version(blockId)).isEqualTo(1);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** 두 작업을 같은 순간에 출발시키고, 각각 성공(true)·409(false) 여부를 돌려준다. 그 외 예외는 그대로 던진다. */
    private List<Boolean> race(Runnable first, Runnable second) throws Exception {
        CyclicBarrier gate = new CyclicBarrier(2);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Runnable task : List.of(first, second)) {
            Callable<Boolean> attempt = () -> {
                gate.await(10, TimeUnit.SECONDS);
                try {
                    task.run();
                    return true;
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) return false;
                    throw e;
                }
            };
            futures.add(pool.submit(attempt));
        }
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> f : futures) results.add(f.get(30, TimeUnit.SECONDS));
        return results;
    }

    private long place(long candidateId, LocalDate date, LocalTime start, int durationMinutes) {
        BlockCreateRequest req = new BlockCreateRequest();
        ReflectionTestUtils.setField(req, "candidateId", candidateId);
        ReflectionTestUtils.setField(req, "tripDate", date);
        ReflectionTestUtils.setField(req, "startTime", start);
        ReflectionTestUtils.setField(req, "durationMinutes", durationMinutes);
        return tripService.placeBlock(tripId, req, ownerId);
    }

    private void move(long blockId, LocalDate date, LocalTime start, int seenVersion) {
        BlockUpdateRequest req = new BlockUpdateRequest();
        ReflectionTestUtils.setField(req, "tripDate", date);
        ReflectionTestUtils.setField(req, "startTime", start);
        ReflectionTestUtils.setField(req, "durationMinutes", 60);
        ReflectionTestUtils.setField(req, "displayOrder", 1);
        ReflectionTestUtils.setField(req, "version", seenVersion);
        tripService.updateBlock(tripId, blockId, req, ownerId);
    }

    /** 좌표가 서로 다른 커스텀 후보(관광지 시드 없이 이동시간 재계산 경로를 탄다). */
    private long candidate() {
        int n = SEQ.incrementAndGet();
        return insert("""
                INSERT INTO trip_candidate (trip_id, source, place_name, place_lat, place_lng)
                VALUES (?, 'CUSTOM', ?, ?, ?)""",
                tripId, "장소" + n,
                new BigDecimal("37.5").add(BigDecimal.valueOf(n, 4)),
                new BigDecimal("127.0").add(BigDecimal.valueOf(n, 4)));
    }

    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    private int blockCount(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_block tb
                JOIN trip_candidate tc ON tc.id = tb.candidate_id
                WHERE tc.trip_id = ? AND tb.trip_date = ?""", Integer.class, tripId, date);
    }

    private int version(long blockId) {
        return jdbc.queryForObject("SELECT version FROM trip_block WHERE id = ?", Integer.class, blockId);
    }

    /** 이 일정 토픽으로 {@code awaited} 이벤트가 나갈 때까지 기다린 뒤, 그때까지 나간 이벤트 타입을 돌려준다. */
    private List<String> awaitEventTypes(String awaited) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messaging, timeout(5000).atLeastOnce()).convertAndSend(eq("/topic/trip/" + tripId),
                (Object) org.mockito.ArgumentMatchers.argThat(o -> o instanceof TripEvent e && e.type().equals(awaited)));
        verify(messaging, atLeastOnce()).convertAndSend(eq("/topic/trip/" + tripId), captor.capture());
        return captor.getAllValues().stream().map(o -> ((TripEvent) o).type()).toList();
    }

    private void awaitTransit(long blockId, int expectedMinutes) {
        org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT transit_duration_minutes FROM trip_block WHERE id = ?",
                        Integer.class, blockId)).isEqualTo(expectedMinutes));
    }
}
