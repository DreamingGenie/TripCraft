package com.tripcraft.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripcraft.plan.dto.TransitResponse;

import java.util.Optional;

public interface TransitService {
    Optional<TransitResponse> getTransitTime(Long fromAttractionId, Long toAttractionId, int departureHour, String mode);
    Optional<JsonNode> getPathDetail(Long fromAttractionId, Long toAttractionId, int departureHour);

    Optional<TransitResponse> selectPath(Long fromAttractionId, Long toAttractionId, int departureHour, int pathIndex);
}
