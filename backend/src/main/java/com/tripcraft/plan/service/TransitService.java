package com.tripcraft.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripcraft.plan.dto.TransitResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TransitService {
    Optional<TransitResponse> getTransitTime(Long fromAttractionId, Long toAttractionId, int departureHour, String mode);
    Optional<JsonNode> getPathDetail(Long fromAttractionId, Long toAttractionId, int departureHour);
    Optional<TransitResponse> selectPath(Long fromAttractionId, Long toAttractionId, int departureHour, int pathIndex);
    List<TransitResponse> getDrivingOptions(Long fromId, Long toId, int departureHour);
    Optional<TransitResponse> getDrivingOption(Long fromId, Long toId, int departureHour, int optionIndex);
    void applyDrivingOption(Long fromId, Long toId, int departureHour, int optionIndex);
    /** loadLane raw_response를 파싱해 구간별 좌표 반환. [{c: modeClass, p: [[lng,lat],...]}] */
    List<Map<String, Object>> getLaneSegments(Long fromId, Long toId, int departureHour);

    /** 도보 경로 좌표 반환. [[lng,lat],...] — 구현체 교체로 provider 변경 가능 */
    List<double[]> getWalkingCoords(double startLat, double startLng, double endLat, double endLng);
}
