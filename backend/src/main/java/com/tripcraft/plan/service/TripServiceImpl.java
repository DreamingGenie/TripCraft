package com.tripcraft.plan.service;

import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.mapper.AttractionMapper;
import com.tripcraft.plan.domain.Trip;
import com.tripcraft.plan.domain.TripBlock;
import com.tripcraft.plan.domain.TripCandidate;
import com.tripcraft.plan.dto.BlockCreateRequest;
import com.tripcraft.plan.dto.BlockItem;
import com.tripcraft.plan.dto.BlockUpdateRequest;
import com.tripcraft.plan.dto.CandidateItem;
import com.tripcraft.plan.dto.TransitResponse;
import com.tripcraft.plan.dto.TripCreateRequest;
import com.tripcraft.plan.dto.TripDetailResponse;
import com.tripcraft.plan.dto.TripSummary;
import com.tripcraft.plan.mapper.TripBlockMapper;
import com.tripcraft.plan.mapper.TripCandidateMapper;
import com.tripcraft.plan.mapper.TripMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
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
    private final AttractionMapper attractionMapper;
    private final TransitService transitService;

    private static final Map<Integer, String> SIDO_NAME = Map.ofEntries(
        Map.entry(1, "서울"), Map.entry(2, "인천"), Map.entry(3, "대전"),
        Map.entry(4, "대구"), Map.entry(5, "광주"), Map.entry(6, "부산"),
        Map.entry(7, "울산"), Map.entry(8, "세종"), Map.entry(31, "경기"),
        Map.entry(32, "강원"), Map.entry(33, "충북"), Map.entry(34, "충남"),
        Map.entry(35, "경북"), Map.entry(36, "경남"), Map.entry(37, "전북"),
        Map.entry(38, "전남"), Map.entry(39, "제주")
    );

    private static final Map<Integer, String> TYPE_NAME = Map.of(
        12, "관광지", 14, "문화시설", 28, "레포츠", 32, "숙박", 38, "쇼핑", 39, "음식점"
    );

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
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!trip.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        List<TripCandidate> candidates = candidateMapper.findByTripId(tripId);
        List<TripBlock> blocks = blockMapper.findByTripId(tripId);

        Map<Long, List<BlockItem>> blocksByCandidate = blocks.stream()
            .collect(Collectors.groupingBy(TripBlock::getCandidateId,
                Collectors.mapping(b -> new BlockItem(b.getId(), b.getCandidateId(),
                    b.getTripDate(), b.getDisplayOrder(), b.getStartTime(), b.getDurationMinutes(),
                    b.getTransitDurationMinutes(), b.getTransitMode()),
                    Collectors.toList())));

        List<CandidateItem> candidateItems = candidates.stream().map(c -> {
            Attraction a = attractionMapper.findById(c.getAttractionId()).orElse(null);
            String name = a != null ? a.getTitle() : "알 수 없음";
            String img = a != null ? a.getFirstImage() : null;
            String cat = a != null ? TYPE_NAME.getOrDefault(a.getContentTypeId(), "") : "";
            String city = SIDO_NAME.getOrDefault(c.getCityCode(), "");
            var lat = a != null ? a.getLatitude() : null;
            var lng = a != null ? a.getLongitude() : null;
            return new CandidateItem(c.getId(), c.getAttractionId(), name, img,
                c.getCityCode(), city, cat, c.getSource(), lat, lng,
                blocksByCandidate.getOrDefault(c.getId(), List.of()));
        }).toList();

        return new TripDetailResponse(trip.getId(), trip.getTitle(),
            trip.getStartDate(), trip.getEndDate(), trip.getMemberCount(), candidateItems);
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
        tripMapper.insert(trip);
        return trip.getId();
    }

    @Override
    @Transactional
    public void deleteTrip(Long tripId, Long memberId) {
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!trip.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (tripMapper.existsPostByTripId(tripId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "공유된 게시글이 있어 삭제할 수 없습니다.");
        }
        tripMapper.deleteById(tripId);
    }

    @Override
    @Transactional
    public Long addCandidate(Long tripId, Long attractionId, Long memberId) {
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!trip.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Attraction a = attractionMapper.findById(attractionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "관광지를 찾을 수 없습니다."));
        TripCandidate candidate = new TripCandidate();
        candidate.setTripId(tripId);
        candidate.setAttractionId(attractionId);
        candidate.setCityCode(a.getSidoCode());
        candidate.setSource("MANUAL");
        candidateMapper.insert(candidate);
        return candidate.getId();
    }

    @Override
    @Transactional
    public void removeCandidate(Long tripId, Long candidateId, Long memberId) {
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!trip.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        candidateMapper.findById(candidateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (candidateMapper.existsBlockByCandidateId(candidateId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "타임라인에 배치된 블록이 있습니다.");
        }
        candidateMapper.deleteById(candidateId);
    }

    @Override
    @Transactional
    public Long placeBlock(Long tripId, BlockCreateRequest req, Long memberId) {
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!trip.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        TripBlock block = new TripBlock();
        block.setCandidateId(req.getCandidateId());
        block.setTripDate(req.getTripDate());
        block.setStartTime(req.getStartTime());
        block.setDurationMinutes(req.getDurationMinutes() != null ? req.getDurationMinutes() : 120);
        block.setDisplayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 1);
        blockMapper.insert(block);
        recalculateTransitForDate(tripId, req.getTripDate());
        return block.getId();
    }

    @Override
    @Transactional
    public void updateBlock(Long tripId, Long blockId, BlockUpdateRequest req, Long memberId) {
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!trip.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        TripBlock block = blockMapper.findById(blockId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate oldDate = block.getTripDate();
        block.setTripDate(req.getTripDate());
        block.setStartTime(req.getStartTime());
        block.setDurationMinutes(req.getDurationMinutes());
        block.setDisplayOrder(req.getDisplayOrder());
        blockMapper.update(block);
        recalculateTransitForDate(tripId, req.getTripDate());
        if (!oldDate.equals(req.getTripDate())) {
            recalculateTransitForDate(tripId, oldDate);
        }
    }

    @Override
    @Transactional
    public void removeBlock(Long tripId, Long blockId, Long memberId) {
        Trip trip = tripMapper.findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!trip.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        TripBlock block = blockMapper.findById(blockId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate date = block.getTripDate();
        blockMapper.deleteById(blockId);
        recalculateTransitForDate(tripId, date);
    }

    private void recalculateTransitForDate(Long tripId, LocalDate date) {
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
                        Optional<TransitResponse> transit = transitService.getTransitTime(fromAttrId, toAttrId, 9);
                        block.setTransitDurationMinutes(transit.map(TransitResponse::getDurationMinutes).orElse(null));
                        block.setTransitMode(transit.map(TransitResponse::getTransportMode).orElse(null));
                        log.debug("Transit 계산: from={} to={} → {}분 ({})",
                                fromAttrId, toAttrId, block.getTransitDurationMinutes(), block.getTransitMode());
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
    }
}
