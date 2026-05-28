package com.tripcraft.plan.service;

import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.mapper.AttractionMapper;
import com.tripcraft.plan.client.OdsayClient;
import com.tripcraft.plan.domain.TransitCache;
import com.tripcraft.plan.dto.TransitResponse;
import com.tripcraft.plan.mapper.TransitCacheMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransitServiceImpl implements TransitService {

    private final TransitCacheMapper transitCacheMapper;
    private final AttractionMapper attractionMapper;
    private final OdsayClient odsayClient;

    @Override
    public Optional<TransitResponse> getTransitTime(Long fromId, Long toId, int departureHour) {
        Optional<TransitCache> cached = transitCacheMapper.findByKey(fromId, toId, departureHour);
        if (cached.isPresent()) {
            TransitCache c = cached.get();
            return Optional.of(new TransitResponse(
                    c.getDurationMinutes(), c.getTransportMode(),
                    c.getTransferCount(), c.getFare(), c.getTotalWalkM()));
        }

        Attraction from = attractionMapper.findById(fromId).orElse(null);
        Attraction to   = attractionMapper.findById(toId).orElse(null);
        if (from == null || to == null
                || from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null   || to.getLongitude() == null) {
            return Optional.empty();
        }

        Optional<OdsayClient.OdsayResult> result = odsayClient.findTransitPath(
                from.getLatitude(), from.getLongitude(),
                to.getLatitude(),   to.getLongitude());

        if (result.isEmpty()) return Optional.empty();

        OdsayClient.OdsayResult r = result.get();

        TransitCache cache = new TransitCache();
        cache.setFromAttractionId(fromId);
        cache.setToAttractionId(toId);
        cache.setDepartureHour(departureHour);
        cache.setDurationMinutes(r.durationMinutes());
        cache.setTransportMode(r.transportMode());
        cache.setTransferCount(r.transferCount());
        cache.setFare(r.fare());
        cache.setTotalDistanceM(r.totalDistanceM());
        cache.setTotalWalkM(r.totalWalkM());
        try {
            transitCacheMapper.insert(cache);
        } catch (Exception e) {
            log.warn("TransitCache 저장 실패: {}", e.getMessage());
        }

        return Optional.of(new TransitResponse(
                r.durationMinutes(), r.transportMode(), r.transferCount(), r.fare(), r.totalWalkM()));
    }
}
