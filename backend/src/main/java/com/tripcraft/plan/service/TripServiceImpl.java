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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final TransitService transitService;
    private final RegionService regionService;
    private final SimpMessagingTemplate messaging;

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

    private void broadcast(Long tripId, TripEvent event) {
        messaging.convertAndSend("/topic/trip/" + tripId, event);
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
    }

    @Override
    @Transactional
    public void removeCollaborator(Long tripId, Long targetMemberId, Long requesterId) {
        assertCanManage(tripId, requesterId);
        collaboratorMapper.delete(tripId, targetMemberId);
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
                    b.getTransitDurationMinutes(), b.getTransitMode(), b.getTransitOptionIndex()),
                    Collectors.toList())));

        Map<Integer, String> sidoLabels = regionService.sidoLabelMap();
        Map<Integer, Map<Integer, String>> sigunguLabels = regionService.sigunguLabelMap();
        List<CandidateItem> candidateItems = candidates.stream().map(c -> {
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
    public void removeCandidate(Long tripId, Long candidateId, Long memberId) {
        assertCanEdit(tripId, memberId);
        candidateMapper.findById(candidateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (candidateMapper.existsBlockByCandidateId(candidateId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "타임라인에 배치된 블록이 있습니다.");
        }
        candidateMapper.deleteById(candidateId);
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
        TripBlock block = new TripBlock();
        block.setCandidateId(req.getCandidateId());
        block.setTripDate(req.getTripDate());
        block.setStartTime(req.getStartTime());
        block.setDurationMinutes(req.getDurationMinutes() != null ? req.getDurationMinutes() : 120);
        block.setDisplayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 1);
        blockMapper.insert(block);
        recalculateTransitForDate(tripId, req.getTripDate());
        broadcast(tripId, TripEvent.of("BLOCK_ADDED", memberId, nickname(memberId),
                Map.of("blockId", block.getId(), "candidateId", req.getCandidateId(),
                       "tripDate", req.getTripDate(), "displayOrder", block.getDisplayOrder())));
        return block.getId();
    }

    @Override
    @Transactional
    public void updateBlock(Long tripId, Long blockId, BlockUpdateRequest req, Long memberId) {
        assertCanEdit(tripId, memberId);
        TripBlock block = blockMapper.findById(blockId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate oldDate = block.getTripDate();
        block.setTripDate(req.getTripDate());
        block.setStartTime(req.getStartTime());
        block.setDurationMinutes(req.getDurationMinutes());
        block.setDisplayOrder(req.getDisplayOrder());

        if (req.getTransitMode() != null) {
            block.setTransitMode(req.getTransitMode());
            block.setTransitDurationMinutes(req.getTransitDurationMinutes());
            block.setTransitOptionIndex(req.getTransitOptionIndex());
            blockMapper.update(block);
        } else {
            blockMapper.update(block);
            recalculateTransitForDate(tripId, req.getTripDate());
            if (!oldDate.equals(req.getTripDate())) {
                recalculateTransitForDate(tripId, oldDate);
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
        TripBlock block = blockMapper.findById(blockId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate date = block.getTripDate();
        blockMapper.deleteById(blockId);
        recalculateTransitForDate(tripId, date);
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
                blockMapper.update(block);
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
