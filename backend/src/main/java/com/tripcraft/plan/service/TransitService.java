package com.tripcraft.plan.service;

import com.tripcraft.plan.dto.TransitResponse;

import java.util.Optional;

public interface TransitService {
    Optional<TransitResponse> getTransitTime(Long fromAttractionId, Long toAttractionId,
                                              int departureHour, int transportType);
}
