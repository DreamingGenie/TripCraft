package com.tripcraft.plan.service;

import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.domain.ContentType;
import com.tripcraft.attraction.mapper.AttractionMapper;
import com.tripcraft.attraction.service.RegionService;
import com.tripcraft.member.domain.Member;
import com.tripcraft.member.mapper.MemberMapper;
import com.tripcraft.plan.domain.Trip;
import com.tripcraft.plan.domain.TripBlock;
import com.tripcraft.plan.domain.TripCandidate;
import com.tripcraft.plan.domain.TripCollaborator;
import com.tripcraft.plan.domain.TripRole;
import com.tripcraft.plan.dto.BlockCreateRequest;
import com.tripcraft.plan.dto.BlockItem;
import com.tripcraft.plan.dto.BlockUpdateRequest;
import com.tripcraft.plan.dto.CandidateItem;
import com.tripcraft.plan.dto.CollaboratorItem;
import com.tripcraft.plan.dto.TransitResponse;
import com.tripcraft.plan.dto.TripBlockSummaryResponse;
import com.tripcraft.plan.dto.TripCreateRequest;
import com.tripcraft.plan.dto.TripDetailResponse;
import com.tripcraft.plan.dto.TripCopyRequest;
import com.tripcraft.plan.dto.TripEvent;
import com.tripcraft.plan.dto.TripSummary;
import com.tripcraft.plan.mapper.TripBlockMapper;
import com.tripcraft.plan.mapper.TripCandidateMapper;
import com.tripcraft.plan.mapper.TripCollaboratorMapper;
import com.tripcraft.plan.mapper.TripMapper;
import com.tripcraft.plan.dto.CustomCandidateRequest;
import com.tripcraft.place.domain.MemberPlace;
import com.tripcraft.place.mapper.MemberPlaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import com.tripcraft.plan.controller.TripPresenceController;
import com.tripcraft.global.security.TripAccessVersion;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripMapper tripMapper;
    private final TripCandidateMapper candidateMapper;
    private final TripBlockMapper blockMapper;
    private final TripCollaboratorMapper collaboratorMapper;
    private final AttractionMapper attractionMapper;
    private final MemberMapper memberMapper;
    private final MemberPlaceMapper memberPlaceMapper;
    private final TransitService transitService;
    private final RegionService regionService;
    private final SimpMessagingTemplate messaging;
    private final TripPresenceController presenceController;
    private final TripAccessVersion accessVersion;

    // ── 동시성 헬퍼 ────────────────────────────────────────────────────────────

    /**
     * 같은 일정·날짜에서 [start, start+duration) 시간대가 기존 블록과 겹치면 거부.
     * 호출 전 tripMapper.lockTripRow(tripId)로 일정을 잠가 동시 배치 race를 직렬화해야 한다.
     */
    private void assertNoOverlap(Long tripId, LocalDate date, java.time.LocalTime startTime,
                                 Integer durationMinutes, Long excludeBlockId) {
        if (startTime == null || durationMinutes == null) return;  // 시간 미확정 블록은 겹침 대상 아님
        int startMin = startTime.getHour() * 60 + startTime.getMinute();
        int endMin = startMin + durationMinutes;
        if (blockMapper.countOverlapping(tripId, date, startMin, endMin, excludeBlockId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "선택한 시간대에 이미 다른 일정이 있어요");
        }
    }

    /** grab(드래그 중) 소유자가 요청자 본인이 아니면 선제 거부. grab은 stale/disconnect로 자동 해제됨. */
    private void assertNotGrabbedByOther(Long tripId, Long blockId, Long memberId) {
        Long owner = presenceController.getGrabOwner(tripId, blockId);
        if (owner != null && !owner.equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 사용자가 이 블록을 편집 중입니다");
        }
    }

    /** 외부 API를 포함하는 transit 재계산을 변경 트랜잭션 커밋 후로 미룬다(커넥션 장기 점유 방지). */
    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }

    // ── 권한 헬퍼 ──────────────────────────────────────────────────────────────

    private TripRole resolveRole(Long tripId, Long memberId) {
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (memberId != null && trip.getMemberId().equals(memberId)) return TripRole.OWNER;
        if (memberId != null) {
            TripRole r = collaboratorMapper.findByTripAndMember(tripId, memberId)
                .map(c -> TripRole.valueOf(c.getRole()))
                .orElse(null);
            if (r != null) return r;
        }
        // 링크 공유 폴백: VIEW=조회 / EDIT=로그인 시 편집·비로그인 조회 / PRIVATE=없음
        String access = trip.getShareAccess();
        if ("EDIT".equals(access)) return memberId != null ? TripRole.EDITOR : TripRole.VIEWER;
        if ("VIEW".equals(access)) return TripRole.VIEWER;
        return null;
    }

    private void assertCanView(Long tripId, Long memberId) {
        if (resolveRole(tripId, memberId) == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void assertCanEdit(Long tripId, Long memberId) {
        TripRole r = resolveRole(tripId, memberId);
        if (r == null || r == TripRole.VIEWER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void assertCanManage(Long tripId, Long memberId) {
        if (resolveRole(tripId, memberId) != TripRole.OWNER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private String generateShareToken() {
        byte[] buf = new byte[16];
        new java.security.SecureRandom().nextBytes(buf);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf); // 22자
    }

    // ── 공유 링크 ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public String setShareAccess(Long tripId, String access, Long requesterId) {
        assertCanManage(tripId, requesterId);
        if (!"PRIVATE".equals(access) && !"VIEW".equals(access) && !"EDIT".equals(access))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid access");
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String token = trip.getShareToken();
        if (!"PRIVATE".equals(access) && token == null) token = generateShareToken();
        tripMapper.updateShare(tripId, access, token);
        accessVersion.bump(tripId);  // 공유 접근 레벨 변경 → 캐시된 권한 무효화
        return token;
    }

    @Override
    public TripDetailResponse getSharedTrip(String token, Long memberId) {
        Trip trip = tripMapper.findByShareToken(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (trip.getShareAccess() == null || "PRIVATE".equals(trip.getShareAccess()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        // assertCanView 는 share_access 폴백으로 통과 (비로그인 포함)
        return getTripDetail(trip.getId(), memberId);
    }

    private String nickname(Long memberId) {
        return memberMapper.findById(memberId).map(Member::getNickname).orElse("(알 수 없음)");
    }

    // 일정별 편집 이벤트 시퀀스(단조 증가). 클라이언트가 순서 역전·중복을 거를 수 있게 한다.
    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicLong> eventSeq
            = new java.util.concurrent.ConcurrentHashMap<>();

    private void broadcast(Long tripId, TripEvent event) {
        long seq = eventSeq.computeIfAbsent(tripId, k -> new java.util.concurrent.atomic.AtomicLong())
                           .incrementAndGet();
        messaging.convertAndSend("/topic/trip/" + tripId, event.withSeq(seq));
    }

    // ── 협업자 관리 ────────────────────────────────────────────────────────────

    @Override
    public List<TripSummary> getCollaboratingTrips(Long memberId) {
        return tripMapper.findCollaboratingByMemberId(memberId).stream()
            .map(t -> {
                int cnt = candidateMapper.findByTripId(t.getId()).size();
                return new TripSummary(t.getId(), t.getTitle(), t.getStartDate(), t.getEndDate(),
                    t.getMemberCount(), cnt);
            })
            .toList();
    }

    @Override
    public List<CollaboratorItem> getCollaborators(Long tripId, Long requesterId) {
        assertCanView(tripId, requesterId);
        return collaboratorMapper.findItemsByTripId(tripId);
    }

    @Override
    @Transactional
    public void inviteCollaborator(Long tripId, Long targetMemberId, String role, Long requesterId) {
        assertCanManage(tripId, requesterId);
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (trip.getMemberId().equals(targetMemberId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "소유자는 협업자로 추가할 수 없습니다.");
        memberMapper.findById(targetMemberId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));

        TripCollaborator collab = new TripCollaborator();
        collab.setTripId(tripId);
        collab.setMemberId(targetMemberId);
        collab.setRole(role != null ? role : "EDITOR");
        collaboratorMapper.insert(collab);
        accessVersion.bump(tripId);  // 협업자 추가 → 캐시된 권한 무효화(재초대 시 차단 해제)
    }

    @Override
    @Transactional
    public void removeCollaborator(Long tripId, Long targetMemberId, Long requesterId) {
        assertCanManage(tripId, requesterId);
        collaboratorMapper.delete(tripId, targetMemberId);
        accessVersion.bump(tripId);  // 협업자 제거 → 캐시된 권한 즉시 무효화(presence 전송 차단)
    }

    // ── 기존 조회/편집 메서드 ──────────────────────────────────────────────────

    @Override
    public List<TripSummary> getMyTrips(Long memberId) {
        return tripMapper.findByMemberId(memberId).stream()
            .map(t -> {
                int cnt = candidateMapper.findByTripId(t.getId()).size();
                return new TripSummary(t.getId(), t.getTitle(), t.getStartDate(), t.getEndDate(),
                    t.getMemberCount(), cnt);
            })
            .toList();
    }

    @Override
    public TripDetailResponse getTripDetail(Long tripId, Long memberId) {
        assertCanView(tripId, memberId);
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<TripCandidate> candidates = candidateMapper.findByTripId(tripId);
        List<TripBlock> blocks = blockMapper.findByTripId(tripId);

        Map<Long, List<BlockItem>> blocksByCandidate = blocks.stream()
            .collect(Collectors.groupingBy(TripBlock::getCandidateId,
                Collectors.mapping(b -> new BlockItem(b.getId(), b.getCandidateId(),
                    b.getTripDate(), b.getDisplayOrder(), b.getStartTime(), b.getDurationMinutes(),
                    b.getTransitDurationMinutes(), b.getTransitMode(), b.getTransitOptionIndex(),
                    b.getVersion()),
                    Collectors.toList())));

        Map<Integer, String> sidoLabels = regionService.sidoLabelMap();
        Map<Integer, Map<Integer, String>> sigunguLabels = regionService.sigunguLabelMap();
        List<CandidateItem> candidateItems = candidates.stream().map(c -> {
            if (c.getAttractionId() == null) {  // 커스텀 장소
                return new CandidateItem(c.getId(), null, c.getPlaceName(), null,
                    null, "", null, c.getPlaceAddress() != null ? c.getPlaceAddress() : "",
                    c.getPlaceCategory() != null ? c.getPlaceCategory() : "관광지", c.getSource(),
                    c.getPlaceLat(), c.getPlaceLng(),
                    blocksByCandidate.getOrDefault(c.getId(), List.of()));
            }
            Attraction a = attractionMapper.findById(c.getAttractionId()).orElse(null);
            String name = a != null ? a.getTitle() : "알 수 없음";
            String img = a != null ? a.getFirstImage() : null;
            String cat = a != null ? ContentType.labelOf(a.getContentTypeId()) : "";
            String city = sidoLabels.getOrDefault(c.getCityCode(), "");
            Integer sgCode = a != null ? a.getSigunguCode() : null;
            String sgName = sgCode == null ? ""
                : sigunguLabels.getOrDefault(c.getCityCode(), Map.of()).getOrDefault(sgCode, "");
            var lat = a != null ? a.getLatitude() : null;
            var lng = a != null ? a.getLongitude() : null;
            return new CandidateItem(c.getId(), c.getAttractionId(), name, img,
                c.getCityCode(), city, sgCode, sgName, cat, c.getSource(), lat, lng,
                blocksByCandidate.getOrDefault(c.getId(), List.of()));
        }).toList();

        String ownerNickname = memberMapper.findById(trip.getMemberId())
            .map(Member::getNickname).orElse("");
        String myRole = resolveRole(tripId, memberId).name();

        return new TripDetailResponse(trip.getId(), trip.getTitle(),
            trip.getStartDate(), trip.getEndDate(), trip.getMemberCount(),
            trip.getDefaultTransitMode(), ownerNickname, myRole, candidateItems,
            trip.getShareAccess(), trip.getShareToken());
    }

    @Override
    @Transactional
    public Long createTrip(TripCreateRequest req, Long memberId) {
        Trip trip = new Trip();
        trip.setMemberId(memberId);
        trip.setTitle(req.getTitle());
        trip.setStartDate(req.getStartDate());
        trip.setEndDate(req.getEndDate());
        trip.setMemberCount(req.getMemberCount() != null ? req.getMemberCount() : 1);
        trip.setIsPublic(false);
        trip.setDefaultTransitMode(req.getDefaultTransitMode() != null ? req.getDefaultTransitMode() : "PUBLIC_TRANSIT");
        tripMapper.insert(trip);
        return trip.getId();
    }

    @Override
    @Transactional
    public void deleteTrip(Long tripId, Long memberId) {
        assertCanManage(tripId, memberId);
        if (tripMapper.existsPostByTripId(tripId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "공유된 게시글이 있어 삭제할 수 없습니다.");
        }
        tripMapper.deleteById(tripId);
    }

    @Override
    @Transactional
    public Long addCandidate(Long tripId, Long attractionId, Long memberId) {
        assertCanEdit(tripId, memberId);
        Attraction a = attractionMapper.findById(attractionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "관광지를 찾을 수 없습니다."));
        TripCandidate candidate = new TripCandidate();
        candidate.setTripId(tripId);
        candidate.setAttractionId(attractionId);
        candidate.setCityCode(a.getSidoCode());
        candidate.setSource("MANUAL");
        candidateMapper.insert(candidate);
        broadcast(tripId, TripEvent.of("CANDIDATE_ADDED", memberId, nickname(memberId),
                Map.of("candidateId", candidate.getId(), "attractionId", attractionId)));
        return candidate.getId();
    }

    @Override
    @Transactional
    public Long addCustomCandidate(Long tripId, CustomCandidateRequest req, Long memberId) {
        assertCanEdit(tripId, memberId);
        if (req.getName() == null || req.getName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장소명이 필요합니다.");
        String category = req.getCategory() != null ? req.getCategory() : "관광지";
        TripCandidate c = new TripCandidate();
        c.setTripId(tripId);
        c.setSource("CUSTOM");
        c.setPlaceName(req.getName());
        c.setPlaceCategory(category);
        c.setPlaceAddress(req.getAddress());
        c.setPlaceLat(req.getLatitude());
        c.setPlaceLng(req.getLongitude());
        candidateMapper.insert(c);
        if (req.isSaveToMyPlaces()) {
            MemberPlace p = new MemberPlace();
            p.setMemberId(memberId);
            p.setName(req.getName());
            p.setCategory(category);
            p.setAddress(req.getAddress());
            p.setLatitude(req.getLatitude());
            p.setLongitude(req.getLongitude());
            memberPlaceMapper.insert(p);
        }
        broadcast(tripId, TripEvent.of("CANDIDATE_ADDED", memberId, nickname(memberId),
                Map.of("candidateId", c.getId())));
        return c.getId();
    }

    @Override
    @Transactional
    public Long addCandidateFromMyPlace(Long tripId, Long placeId, Long memberId) {
        assertCanEdit(tripId, memberId);
        MemberPlace p = memberPlaceMapper.findById(placeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."));
        if (!p.getMemberId().equals(memberId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        TripCandidate c = new TripCandidate();
        c.setTripId(tripId);
        c.setSource("CUSTOM");
        c.setPlaceName(p.getName());
        c.setPlaceCategory(p.getCategory());
        c.setPlaceAddress(p.getAddress());
        c.setPlaceLat(p.getLatitude());
        c.setPlaceLng(p.getLongitude());
        candidateMapper.insert(c);
        broadcast(tripId, TripEvent.of("CANDIDATE_ADDED", memberId, nickname(memberId),
                Map.of("candidateId", c.getId())));
        return c.getId();
    }

    @Override
    @Transactional
    public void removeCandidate(Long tripId, Long candidateId, Long memberId) {
        assertCanEdit(tripId, memberId);
        candidateMapper.findById(candidateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // 흔한 경우 선제 체크(친절한 메시지). 단, 이 체크와 삭제 사이에 동시 placeBlock이 끼는
        // TOCTOU 창이 있으므로, 실제 삭제는 FK(RESTRICT) 위반을 잡아 동일 메시지로 변환한다.
        if (candidateMapper.existsBlockByCandidateId(candidateId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "타임라인에 배치된 블록이 있습니다.");
        }
        try {
            candidateMapper.deleteById(candidateId);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "타임라인에 배치된 블록이 있습니다.");
        }
        broadcast(tripId, TripEvent.of("CANDIDATE_REMOVED", memberId, nickname(memberId),
                Map.of("candidateId", candidateId)));
    }

    @Override
    @Transactional
    public Long placeBlock(Long tripId, BlockCreateRequest req, Long memberId) {
        assertCanEdit(tripId, memberId);
        TripCandidate candidate = candidateMapper.findById(req.getCandidateId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후보군을 찾을 수 없습니다."));
        if (!candidate.getTripId().equals(tripId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 일정의 후보군입니다.");
        tripMapper.lockTripRow(tripId);  // 같은 일정 동시 편집 직렬화(겹침 검사·순서 할당 정합 보장)
        int duration = req.getDurationMinutes() != null ? req.getDurationMinutes() : 120;
        assertNoOverlap(tripId, req.getTripDate(), req.getStartTime(), duration, null);
        TripBlock block = new TripBlock();
        block.setCandidateId(req.getCandidateId());
        block.setTripDate(req.getTripDate());
        block.setStartTime(req.getStartTime());
        block.setDurationMinutes(duration);
        // display_order는 클라이언트 값을 믿지 않고 서버가 권위적으로 할당(동시 배치 시 순서 충돌 완화)
        block.setDisplayOrder(blockMapper.nextDisplayOrder(tripId, req.getTripDate()));
        blockMapper.insert(block);
        runAfterCommit(() -> recalculateTransitForDate(tripId, req.getTripDate()));
        broadcast(tripId, TripEvent.of("BLOCK_ADDED", memberId, nickname(memberId),
                Map.of("blockId", block.getId(), "candidateId", req.getCandidateId(),
                       "tripDate", req.getTripDate(), "displayOrder", block.getDisplayOrder())));
        return block.getId();
    }

    @Override
    @Transactional
    public void updateBlock(Long tripId, Long blockId, BlockUpdateRequest req, Long memberId) {
        assertCanEdit(tripId, memberId);
        assertNotGrabbedByOther(tripId, blockId, memberId);
        TripBlock block = blockMapper.findById(blockId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (req.getTransitMode() != null) {
            // 사용자가 transit 옵션 직접 선택 — transit 컬럼만 갱신(version·위치 미변경)
            blockMapper.updateTransitById(blockId, req.getTransitDurationMinutes(),
                    req.getTransitMode(), req.getTransitOptionIndex());
        } else {
            // 위치 편집(이동/리사이즈) — 낙관적 락
            tripMapper.lockTripRow(tripId);  // 동시 편집 직렬화 → 겹침 검사가 최신 커밋 상태를 봄
            assertNoOverlap(tripId, req.getTripDate(), req.getStartTime(), req.getDurationMinutes(), blockId);
            LocalDate oldDate = block.getTripDate();
            block.setTripDate(req.getTripDate());
            block.setStartTime(req.getStartTime());
            block.setDurationMinutes(req.getDurationMinutes());
            // 다른 날짜로 이동하면 그 날짜 기준으로 서버가 순서를 재할당(클라 값은 새 날짜에서 무의미)
            block.setDisplayOrder(oldDate.equals(req.getTripDate())
                    ? req.getDisplayOrder()
                    : blockMapper.nextDisplayOrder(tripId, req.getTripDate()));
            // 클라이언트가 마지막으로 본 버전. null(구버전 클라이언트)이면 현재 버전으로 폴백(LWW)
            block.setVersion(req.getVersion() != null ? req.getVersion() : block.getVersion());
            int affected = blockMapper.updateWithVersion(block);
            if (affected == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 사용자가 먼저 이 블록을 수정했어요");
            }
            runAfterCommit(() -> recalculateTransitForDate(tripId, req.getTripDate()));
            if (!oldDate.equals(req.getTripDate())) {
                runAfterCommit(() -> recalculateTransitForDate(tripId, oldDate));
            }
        }
        broadcast(tripId, TripEvent.of("BLOCK_MOVED", memberId, nickname(memberId),
                Map.of("blockId", blockId, "tripDate", req.getTripDate(),
                       "displayOrder", req.getDisplayOrder())));
    }

    @Override
    @Transactional
    public void removeBlock(Long tripId, Long blockId, Long memberId) {
        assertCanEdit(tripId, memberId);
        assertNotGrabbedByOther(tripId, blockId, memberId);
        TripBlock block = blockMapper.findById(blockId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate date = block.getTripDate();
        blockMapper.deleteById(blockId);
        runAfterCommit(() -> recalculateTransitForDate(tripId, date));
        broadcast(tripId, TripEvent.of("BLOCK_DELETED", memberId, nickname(memberId),
                Map.of("blockId", blockId, "tripDate", date)));
    }

    @Override
    @Transactional
    public void updateDefaultTransitMode(Long tripId, String mode, Long memberId) {
        assertCanEdit(tripId, memberId);
        tripMapper.updateDefaultTransitMode(tripId, mode);
    }

    @Override
    public TripBlockSummaryResponse getBlocksSummary(Long tripId) {
        if (!tripMapper.existsPostByTripId(tripId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "공유되지 않은 일정입니다.");
        }

        List<TripBlockSummaryResponse.BlockRow> rows = tripMapper.findBlocksSummary(tripId);

        // 날짜 순서 유지하며 그룹핑 후 응답 DTO로 변환
        LinkedHashMap<LocalDate, List<TripBlockSummaryResponse.BlockItem>> grouped = new LinkedHashMap<>();
        for (TripBlockSummaryResponse.BlockRow row : rows) {
            grouped.computeIfAbsent(row.getTripDate(), k -> new ArrayList<>())
                   .add(new TripBlockSummaryResponse.BlockItem(
                           row.getAttractionName(), row.getStartTime(), row.getDurationMinutes()));
        }

        List<TripBlockSummaryResponse.DaySummary> days = grouped.entrySet().stream()
            .map(e -> new TripBlockSummaryResponse.DaySummary(e.getKey(), e.getValue()))
            .toList();

        return new TripBlockSummaryResponse(days);
    }

    /**
     * 공유된 일정을 복사해 요청자 소유의 새 일정으로 저장.
     * - 날짜: newStartDate 기준으로 Day 간격 그대로 유지 (ChronoUnit.DAYS 오프셋)
     * - 후보군·블록 전체 복제 (candidateId 매핑 테이블로 FK 일관성 보장)
     * - 이동 시간(transitDurationMinutes/transitMode)은 위치가 동일하므로 그대로 복사
     * - 복사된 일정은 비공개(isPublic=false)로 생성
     */
    @Override
    @Transactional
    public Long copyTrip(Long sourceTripId, TripCopyRequest request, Long memberId) {
        Trip source = tripMapper.findById(sourceTripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));

        // 공유된 일정(커뮤니티에 연결된 일정)만 복사 허용
        if (!tripMapper.existsPostByTripId(sourceTripId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "공유되지 않은 일정은 가져올 수 없습니다.");
        }

        // 날짜 오프셋 계산 — 원본 Day 간격 유지
        long dayOffset = ChronoUnit.DAYS.between(source.getStartDate(), request.getNewStartDate());
        LocalDate newEndDate = source.getEndDate().plusDays(dayOffset);

        // 새 일정 생성
        Trip newTrip = new Trip();
        newTrip.setMemberId(memberId);
        newTrip.setTitle(source.getTitle() + " (가져옴)");
        newTrip.setStartDate(request.getNewStartDate());
        newTrip.setEndDate(newEndDate);
        newTrip.setMemberCount(source.getMemberCount());
        newTrip.setDefaultTransitMode(source.getDefaultTransitMode());
        newTrip.setIsPublic(false);
        tripMapper.insert(newTrip);

        // 후보군 복사 + candidateId 매핑 (원본 id → 새 id)
        List<TripCandidate> candidates = candidateMapper.findByTripId(sourceTripId);
        Map<Long, Long> candidateIdMap = new HashMap<>();
        for (TripCandidate c : candidates) {
            TripCandidate newC = new TripCandidate();
            newC.setTripId(newTrip.getId());
            newC.setAttractionId(c.getAttractionId());
            newC.setCityCode(c.getCityCode());
            newC.setSource("MANUAL");
            candidateMapper.insert(newC);
            candidateIdMap.put(c.getId(), newC.getId());
        }

        // 블록 복사 — 날짜만 오프셋 적용, 이동 시간은 위치 동일하므로 유지
        List<TripBlock> blocks = blockMapper.findByTripId(sourceTripId);
        for (TripBlock b : blocks) {
            Long newCandidateId = candidateIdMap.get(b.getCandidateId());
            if (newCandidateId == null) continue;
            TripBlock newB = new TripBlock();
            newB.setCandidateId(newCandidateId);
            newB.setTripDate(b.getTripDate().plusDays(dayOffset));
            newB.setDisplayOrder(b.getDisplayOrder());
            newB.setStartTime(b.getStartTime());
            newB.setDurationMinutes(b.getDurationMinutes());
            newB.setTransitDurationMinutes(b.getTransitDurationMinutes());
            newB.setTransitMode(b.getTransitMode());
            blockMapper.insert(newB);
        }

        log.info("일정 복사 완료: sourceTripId={} → newTripId={}, memberId={}, dayOffset={}",
                sourceTripId, newTrip.getId(), memberId, dayOffset);
        return newTrip.getId();
    }

    private void recalculateTransitForDate(Long tripId, LocalDate date) {
        Trip trip = tripMapper.findById(tripId).orElse(null);
        String transitMode = (trip != null && trip.getDefaultTransitMode() != null)
                ? trip.getDefaultTransitMode() : "PUBLIC_TRANSIT";

        List<TripBlock> blocks;
        try {
            blocks = blockMapper.findByTripIdAndDate(tripId, date);
        } catch (Exception e) {
            log.warn("Transit 재계산 실패 (block 조회): tripId={}, date={}: {}", tripId, date, e.getMessage());
            return;
        }
        blocks.sort(Comparator.comparing(
                b -> Optional.ofNullable(((TripBlock) b).getStartTime()).orElse(java.time.LocalTime.MAX)));
        for (int i = 0; i < blocks.size(); i++) {
            TripBlock block = blocks.get(i);
            try {
                if (i == 0) {
                    block.setTransitDurationMinutes(null);
                    block.setTransitMode(null);
                } else {
                    TripBlock prev = blocks.get(i - 1);
                    Optional<TripCandidate> fromCand = candidateMapper.findById(prev.getCandidateId());
                    Optional<TripCandidate> toCand   = candidateMapper.findById(block.getCandidateId());
                    if (fromCand.isPresent() && toCand.isPresent()) {
                        long fromAttrId = fromCand.get().getAttractionId();
                        long toAttrId   = toCand.get().getAttractionId();
                        String savedMode = block.getTransitMode();
                        // savedMode는 ODsay 결과(BUS/SUBWAY 등) 또는 요청 모드(DRIVING/WALKING)가 혼재함.
                        // getTransitTime의 requestMode는 PUBLIC_TRANSIT/DRIVING/WALKING 세 가지만 유효하므로
                        // 저장된 값을 적절한 요청 모드로 매핑한다.
                        String effectiveMode;
                        if ("DRIVING".equals(savedMode)) {
                            effectiveMode = "DRIVING";
                        } else if ("WALKING".equals(savedMode)) {
                            effectiveMode = "WALKING";
                        } else if (savedMode != null && !"NONE".equals(savedMode)) {
                            // BUS, SUBWAY, BUS,SUBWAY 등 대중교통 세부 모드
                            effectiveMode = "PUBLIC_TRANSIT";
                        } else {
                            // null 또는 NONE → trip 기본 모드
                            effectiveMode = transitMode;
                        }
                        Optional<TransitResponse> transit = transitService.getTransitTime(fromAttrId, toAttrId, 9, effectiveMode);
                        // 대중교통이 기본 모드인데 경로가 없으면 자동차로 fallback
                        if ("PUBLIC_TRANSIT".equals(effectiveMode)
                                && transit.isPresent() && "NONE".equals(transit.get().getTransportMode())) {
                            Optional<TransitResponse> fallback = transitService.getTransitTime(fromAttrId, toAttrId, 9, "DRIVING");
                            if (fallback.isPresent() && !"NONE".equals(fallback.get().getTransportMode())) {
                                transit = fallback;
                            }
                        }
                        block.setTransitDurationMinutes(transit.map(TransitResponse::getDurationMinutes).orElse(null));
                        // 사용자가 직접 선택한 DRIVING/WALKING은 모드 유지, 그 외는 재계산 결과로 갱신
                        boolean userOverride = "DRIVING".equals(savedMode) || "WALKING".equals(savedMode);
                        if (!userOverride) {
                            block.setTransitMode(transit.map(TransitResponse::getTransportMode).orElse(null));
                        }
                        log.debug("Transit 계산: from={} to={} → {}분 ({}) mode={}",
                                fromAttrId, toAttrId, block.getTransitDurationMinutes(), block.getTransitMode(), effectiveMode);
                    } else {
                        block.setTransitDurationMinutes(null);
                        block.setTransitMode(null);
                    }
                }
                // transit 전용 갱신 — 사용자의 위치/순서 변경이나 version 을 건드리지 않는다(오탐 방지)
                blockMapper.updateTransitById(block.getId(), block.getTransitDurationMinutes(),
                        block.getTransitMode(), block.getTransitOptionIndex());
            } catch (Exception e) {
                log.warn("Transit 재계산 실패 (blockId={}): tripId={}, date={}: {}",
                        block.getId(), tripId, date, e.getMessage());
            }
        }

        // transit 재계산 완료 브로드캐스트
        List<Map<String, Object>> transitSummary = blocks.stream()
                .map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("blockId", b.getId());
                    m.put("transitDurationMinutes", b.getTransitDurationMinutes());
                    m.put("transitMode", b.getTransitMode());
                    return m;
                })
                .toList();
        broadcast(tripId, TripEvent.of("TRANSIT_RECALCULATED", null, null,
                Map.of("tripDate", date, "blocks", transitSummary)));
    }
}
