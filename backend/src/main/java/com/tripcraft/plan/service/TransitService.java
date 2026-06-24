package com.tripcraft.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripcraft.plan.dto.TransitResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TransitService {
    Optional<TransitResponse> getTransitTime(Long fromAttractionId, Long toAttractionId, int departureHour, String mode);

    /** 좌표 기반 이동시간(커스텀 장소용, 무캐시 라이브). 대중교통=ODsay best, 운전/도보=TMap. */
    Optional<TransitResponse> getTransitByCoords(java.math.BigDecimal fromLat, java.math.BigDecimal fromLng,
                                                 java.math.BigDecimal toLat, java.math.BigDecimal toLng,
                                                 int departureHour, String mode);

    Optional<JsonNode> getPathDetail(Long fromAttractionId, Long toAttractionId, int departureHour);

    /** 좌표 기반 대중교통 경로 단계(detail). attraction getPathDetail 과 동일 구조, 무캐시 라이브. */
    Optional<JsonNode> getTransitDetailByCoords(java.math.BigDecimal fromLat, java.math.BigDecimal fromLng,
                                                java.math.BigDecimal toLat, java.math.BigDecimal toLng, int departureHour);

    /** 좌표 기반 자동차 단일 옵션(optionIndex). 무캐시 라이브. */
    Optional<TransitResponse> getDrivingOptionByCoords(java.math.BigDecimal fromLat, java.math.BigDecimal fromLng,
                                                       java.math.BigDecimal toLat, java.math.BigDecimal toLng,
                                                       int departureHour, int optionIndex);

    Optional<TransitResponse> selectPath(Long fromAttractionId, Long toAttractionId, int departureHour, int pathIndex);
    Optional<TransitResponse> getDrivingOption(Long fromId, Long toId, int departureHour, int optionIndex);
    void applyDrivingOption(Long fromId, Long toId, int departureHour, int optionIndex);

    /** 통합 경로 구간(어트랙션). [{c:class, t:호선/버스종류, name:승차역, end:하차역, p:[[lng,lat],...]}] */
    List<Map<String, Object>> getRouteSegments(Long fromId, Long toId, int departureHour);

    /** 통합 경로 구간(커스텀 좌표). */
    List<Map<String, Object>> getRouteSegmentsByCoords(java.math.BigDecimal fromLat, java.math.BigDecimal fromLng,
                                                       java.math.BigDecimal toLat, java.math.BigDecimal toLng, int departureHour);

    /** 도보 경로 좌표 반환. [[lng,lat],...] — 구현체 교체로 provider 변경 가능 */
    List<double[]> getWalkingCoords(double startLat, double startLng, double endLat, double endLng);
}
