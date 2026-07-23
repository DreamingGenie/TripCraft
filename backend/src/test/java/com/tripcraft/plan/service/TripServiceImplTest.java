package com.tripcraft.plan.service;

import com.tripcraft.attraction.mapper.AttractionMapper;
import com.tripcraft.attraction.service.RegionService;
import com.tripcraft.global.security.TripAccessVersion;
import com.tripcraft.member.mapper.MemberMapper;
import com.tripcraft.place.mapper.MemberPlaceMapper;
import com.tripcraft.plan.controller.TripPresenceController;
import com.tripcraft.plan.domain.Trip;
import com.tripcraft.plan.domain.TripBlock;
import com.tripcraft.plan.domain.TripCandidate;
import com.tripcraft.plan.domain.TripCollaborator;
import com.tripcraft.plan.dto.BlockCreateRequest;
import com.tripcraft.plan.dto.BlockMemoUpdateRequest;
import com.tripcraft.plan.dto.BlockUpdateRequest;
import com.tripcraft.plan.dto.TripEvent;
import com.tripcraft.plan.mapper.TripBlockMapper;
import com.tripcraft.plan.mapper.TripCandidateMapper;
import com.tripcraft.plan.mapper.TripCollaboratorMapper;
import com.tripcraft.plan.mapper.TripMapper;
import com.tripcraft.plan.service.TransitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TripServiceImpl 순수 단위테스트(Mockito, DB·Docker 불필요).
 *
 * <p>가장 사고가 잦은 로직 — 권한 판정, grab 잠금 게이트, 시간 겹침, 낙관적 락, 블록 메모(신규) — 를 덮는다.
 * broadcast()는 트랜잭션 밖에서 runAfterCommit 을 즉시 실행하므로 브로드캐스트까지 검증된다.
 */
@ExtendWith(MockitoExtension.class)
class TripServiceImplTest {

    @Mock TripMapper tripMapper;
    @Mock TripCandidateMapper candidateMapper;
    @Mock TripBlockMapper blockMapper;
    @Mock TripCollaboratorMapper collaboratorMapper;
    @Mock AttractionMapper attractionMapper;
    @Mock MemberMapper memberMapper;
    @Mock MemberPlaceMapper memberPlaceMapper;
    @Mock TransitService transitService;
    @Mock RegionService regionService;
    @Mock SimpMessagingTemplate messaging;
    @Mock TripPresenceController presenceController;
    @Mock TripAccessVersion accessVersion;

    @InjectMocks TripServiceImpl service;

    static final long TRIP_ID = 10L;
    static final long BLOCK_ID = 100L;
    static final long OWNER_ID = 1L;
    static final long OTHER_ID = 2L;

    Trip ownerTrip;

    @BeforeEach
    void setUp() {
        ownerTrip = new Trip();
        ownerTrip.setId(TRIP_ID);
        ownerTrip.setMemberId(OWNER_ID);
        ownerTrip.setShareAccess("PRIVATE");
        ownerTrip.setDefaultTransitMode("PUBLIC_TRANSIT");
    }

    /** OWNER 권한(assertCanEdit 통과)용 공통 스텁. */
    private void stubOwnerTrip() {
        when(tripMapper.findById(TRIP_ID)).thenReturn(Optional.of(ownerTrip));
    }

    /**
     * grab 잠금 없음(아무도 편집 중 아님). getGrabOwner 의 Mockito 기본값이 null 이 아니라 0 이라
     * (숫자 래퍼 기본값) 명시적으로 null 을 준다 → assertNotGrabbedByOther 게이트 통과.
     */
    private void noGrab() {
        when(presenceController.getGrabOwner(TRIP_ID, BLOCK_ID)).thenReturn(null);
    }

    /** 이 일정 편집 토픽으로 나간 모든 브로드캐스트(placeBlock 등은 TRANSIT_RECALCULATED 도 함께 나감). */
    private void assertBroadcast(String type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messaging, org.mockito.Mockito.atLeastOnce())
                .convertAndSend(eq("/topic/trip/" + TRIP_ID), captor.capture());
        assertThat(captor.getAllValues().stream().map(o -> (TripEvent) o))
                .anyMatch(e -> e.type().equals(type));
    }

    // ── updateBlockMemo (신규 기능) ──────────────────────────────────────────

    @Nested
    @DisplayName("updateBlockMemo")
    class UpdateBlockMemo {

        private BlockMemoUpdateRequest req(String memo) {
            BlockMemoUpdateRequest r = new BlockMemoUpdateRequest();
            org.springframework.test.util.ReflectionTestUtils.setField(r, "memo", memo);
            return r;
        }

        @Test
        @DisplayName("정상 저장 → updateMemoById 호출 + BLOCK_MEMO_UPDATED 브로드캐스트")
        void happyPath() {
            stubOwnerTrip();
            noGrab();
            when(blockMapper.findTripIdByBlockId(BLOCK_ID)).thenReturn(Optional.of(TRIP_ID));

            service.updateBlockMemo(TRIP_ID, BLOCK_ID, req("점심 예약 필요"), OWNER_ID);

            verify(blockMapper).updateMemoById(BLOCK_ID, "점심 예약 필요");
            assertBroadcast("BLOCK_MEMO_UPDATED");
        }

        @Test
        @DisplayName("101자 초과 → 400, 저장 안 함")
        void tooLong() {
            stubOwnerTrip();
            noGrab();
            when(blockMapper.findTripIdByBlockId(BLOCK_ID)).thenReturn(Optional.of(TRIP_ID));
            String over = "가".repeat(101);

            assertThatThrownBy(() -> service.updateBlockMemo(TRIP_ID, BLOCK_ID, req(over), OWNER_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("100");
            verify(blockMapper, never()).updateMemoById(anyLong(), any());
        }

        @Test
        @DisplayName("정확히 100자(이모지 포함, 코드포인트 기준) → 저장 허용")
        void exactly100CodePoints() {
            stubOwnerTrip();
            noGrab();
            when(blockMapper.findTripIdByBlockId(BLOCK_ID)).thenReturn(Optional.of(TRIP_ID));
            String memo = "😀".repeat(100);   // 코드포인트 100 (UTF-16 length 200)

            service.updateBlockMemo(TRIP_ID, BLOCK_ID, req(memo), OWNER_ID);

            verify(blockMapper).updateMemoById(BLOCK_ID, memo);
        }

        @Test
        @DisplayName("다른 사용자가 grab 중 → 409, 저장 안 함")
        void grabbedByOther() {
            stubOwnerTrip();
            when(presenceController.getGrabOwner(TRIP_ID, BLOCK_ID)).thenReturn(OTHER_ID);

            assertThatThrownBy(() -> service.updateBlockMemo(TRIP_ID, BLOCK_ID, req("x"), OWNER_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("편집 중");
            verify(blockMapper, never()).updateMemoById(anyLong(), any());
        }

        @Test
        @DisplayName("블록이 다른 일정 소속 → 403")
        void blockFromOtherTrip() {
            stubOwnerTrip();
            noGrab();
            when(blockMapper.findTripIdByBlockId(BLOCK_ID)).thenReturn(Optional.of(999L));

            assertThatThrownBy(() -> service.updateBlockMemo(TRIP_ID, BLOCK_ID, req("x"), OWNER_ID))
                    .isInstanceOf(ResponseStatusException.class);
            verify(blockMapper, never()).updateMemoById(anyLong(), any());
        }

        @Test
        @DisplayName("VIEWER 권한 → 403, 저장 안 함")
        void viewerForbidden() {
            // 소유자가 아닌 협업자 VIEWER
            when(tripMapper.findById(TRIP_ID)).thenReturn(Optional.of(ownerTrip));
            TripCollaborator viewer = new TripCollaborator();
            viewer.setRole("VIEWER");
            when(collaboratorMapper.findByTripAndMember(TRIP_ID, OTHER_ID)).thenReturn(Optional.of(viewer));

            assertThatThrownBy(() -> service.updateBlockMemo(TRIP_ID, BLOCK_ID, req("x"), OTHER_ID))
                    .isInstanceOf(ResponseStatusException.class);
            verify(blockMapper, never()).updateMemoById(anyLong(), any());
        }
    }

    // ── updateBlock (이동/리사이즈 vs transit) ────────────────────────────────

    @Nested
    @DisplayName("updateBlock")
    class UpdateBlock {

        @Test
        @DisplayName("transitMode 지정 → updateTransitById 만 호출(version 미변경 경로)")
        void transitOnlyBranch() {
            stubOwnerTrip();
            noGrab();
            when(blockMapper.findById(BLOCK_ID)).thenReturn(Optional.of(new TripBlock()));
            BlockUpdateRequest req = new BlockUpdateRequest();
            org.springframework.test.util.ReflectionTestUtils.setField(req, "transitMode", "DRIVING");
            org.springframework.test.util.ReflectionTestUtils.setField(req, "transitDurationMinutes", 15);
            org.springframework.test.util.ReflectionTestUtils.setField(req, "transitOptionIndex", 0);
            org.springframework.test.util.ReflectionTestUtils.setField(req, "tripDate", LocalDate.of(2026, 7, 8));
            org.springframework.test.util.ReflectionTestUtils.setField(req, "displayOrder", 1);

            service.updateBlock(TRIP_ID, BLOCK_ID, req, OWNER_ID);

            verify(blockMapper).updateTransitById(BLOCK_ID, 15, "DRIVING", 0);
            verify(blockMapper, never()).updateWithVersion(any());
        }

        @Test
        @DisplayName("위치 편집 낙관적 락 충돌(affected=0) → 409")
        void optimisticLockConflict() {
            stubOwnerTrip();
            noGrab();
            TripBlock existing = new TripBlock();
            existing.setTripDate(LocalDate.of(2026, 7, 8));
            existing.setVersion(3);
            when(blockMapper.findById(BLOCK_ID)).thenReturn(Optional.of(existing));
            when(blockMapper.updateWithVersion(any())).thenReturn(0);

            BlockUpdateRequest req = new BlockUpdateRequest();
            org.springframework.test.util.ReflectionTestUtils.setField(req, "tripDate", LocalDate.of(2026, 7, 8));
            org.springframework.test.util.ReflectionTestUtils.setField(req, "startTime", LocalTime.of(10, 0));
            org.springframework.test.util.ReflectionTestUtils.setField(req, "durationMinutes", 60);
            org.springframework.test.util.ReflectionTestUtils.setField(req, "displayOrder", 1);
            org.springframework.test.util.ReflectionTestUtils.setField(req, "version", 3);

            assertThatThrownBy(() -> service.updateBlock(TRIP_ID, BLOCK_ID, req, OWNER_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("먼저");
        }

        @Test
        @DisplayName("다른 사용자가 grab 중 → 409")
        void grabbedByOther() {
            stubOwnerTrip();
            when(presenceController.getGrabOwner(TRIP_ID, BLOCK_ID)).thenReturn(OTHER_ID);

            assertThatThrownBy(() -> service.updateBlock(TRIP_ID, BLOCK_ID, new BlockUpdateRequest(), OWNER_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("편집 중");
        }
    }

    // ── placeBlock ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("placeBlock")
    class PlaceBlock {

        private BlockCreateRequest createReq() {
            BlockCreateRequest req = new BlockCreateRequest();
            org.springframework.test.util.ReflectionTestUtils.setField(req, "candidateId", 50L);
            org.springframework.test.util.ReflectionTestUtils.setField(req, "tripDate", LocalDate.of(2026, 7, 8));
            org.springframework.test.util.ReflectionTestUtils.setField(req, "startTime", LocalTime.of(10, 0));
            org.springframework.test.util.ReflectionTestUtils.setField(req, "durationMinutes", 60);
            return req;
        }

        private TripCandidate candidateInTrip() {
            TripCandidate c = new TripCandidate();
            c.setId(50L);
            c.setTripId(TRIP_ID);
            return c;
        }

        @Test
        @DisplayName("시간 겹침 → 409")
        void overlapConflict() {
            stubOwnerTrip();
            when(candidateMapper.findById(50L)).thenReturn(Optional.of(candidateInTrip()));
            when(blockMapper.countOverlapping(eq(TRIP_ID), any(), anyInt(), anyInt(), any()))
                    .thenReturn(1);

            assertThatThrownBy(() -> service.placeBlock(TRIP_ID, createReq(), OWNER_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("시간");
            verify(blockMapper, never()).insert(any());
        }

        @Test
        @DisplayName("정상 배치 → 서버가 displayOrder 할당 + BLOCK_ADDED 브로드캐스트")
        void happyPath() {
            stubOwnerTrip();
            when(candidateMapper.findById(50L)).thenReturn(Optional.of(candidateInTrip()));
            when(blockMapper.nextDisplayOrder(eq(TRIP_ID), any())).thenReturn(7);
            // MyBatis useGeneratedKeys 를 흉내 — insert 시 생성 id 채움(broadcast payload NPE 방지)
            org.mockito.Mockito.doAnswer(inv -> {
                inv.getArgument(0, TripBlock.class).setId(200L);
                return null;
            }).when(blockMapper).insert(any());

            service.placeBlock(TRIP_ID, createReq(), OWNER_ID);

            ArgumentCaptor<TripBlock> blockCaptor = ArgumentCaptor.forClass(TripBlock.class);
            verify(blockMapper).insert(blockCaptor.capture());
            assertThat(blockCaptor.getValue().getDisplayOrder()).isEqualTo(7);
            assertBroadcast("BLOCK_ADDED");
        }
    }

    // ── removeCandidate ──────────────────────────────────────────────────────

    @Test
    @DisplayName("removeCandidate — 배치된 블록 존재 시 409")
    void removeCandidateWithBlocks() {
        stubOwnerTrip();
        TripCandidate c = new TripCandidate();
        c.setId(50L);
        when(candidateMapper.findById(50L)).thenReturn(Optional.of(c));
        when(candidateMapper.existsBlockByCandidateId(50L)).thenReturn(true);

        assertThatThrownBy(() -> service.removeCandidate(TRIP_ID, 50L, OWNER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("블록");
        verify(candidateMapper, never()).deleteById(anyLong());
    }

    // ── 권한 폴백 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteTrip — 소유자가 아니면 403")
    void deleteTripNonOwner() {
        when(tripMapper.findById(TRIP_ID)).thenReturn(Optional.of(ownerTrip));
        when(collaboratorMapper.findByTripAndMember(TRIP_ID, OTHER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTrip(TRIP_ID, OTHER_ID))
                .isInstanceOf(ResponseStatusException.class);
        verify(tripMapper, never()).deleteById(anyLong());
    }
}
