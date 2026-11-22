package com.tripcraft.plan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.mapper.AttractionMapper;
import com.tripcraft.plan.client.OdsayClient;
import com.tripcraft.plan.client.TMapClient;
import com.tripcraft.plan.client.TransitLookupException;
import com.tripcraft.plan.domain.TransitCache;
import com.tripcraft.plan.mapper.LanePolylineMapper;
import com.tripcraft.plan.mapper.TransitCacheMapper;
import com.tripcraft.plan.mapper.TripBlockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 외부 API 호출 실패가 "경로 없음(NONE)" 캐시로 굳지 않는지 — 굳으면 그 구간은 장애가 끝나도 계속 NONE 이고,
 * 재계산도 실패를 알지 못한다(2026-11-22 확인한 결함의 회귀 방지).
 */
@ExtendWith(MockitoExtension.class)
class TransitServiceImplTest {

    @Mock TransitCacheMapper transitCacheMapper;
    @Mock LanePolylineMapper lanePolylineMapper;
    @Mock TripBlockMapper tripBlockMapper;
    @Mock AttractionMapper attractionMapper;
    @Mock OdsayClient odsayClient;
    @Mock TMapClient tMapClient;

    TransitServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransitServiceImpl(transitCacheMapper, lanePolylineMapper, tripBlockMapper,
                attractionMapper, odsayClient, tMapClient, new ObjectMapper());
        when(transitCacheMapper.findByKey(anyLong(), anyLong(), anyInt(), anyString())).thenReturn(Optional.empty());
        when(attractionMapper.findById(1L)).thenReturn(Optional.of(attraction("37.5", "127.0")));
        when(attractionMapper.findById(2L)).thenReturn(Optional.of(attraction("37.6", "127.1")));
    }

    @Test
    void 대중교통_호출_실패는_예외이고_캐시하지_않는다() {
        when(odsayClient.findTransitPath(any(), any(), any(), any()))
                .thenThrow(new TransitLookupException("ODsay 오류: code=500"));

        assertThatThrownBy(() -> service.getTransitTime(1L, 2L, 9, "PUBLIC_TRANSIT"))
                .isInstanceOf(TransitLookupException.class);
        verify(transitCacheMapper, never()).insert(any(TransitCache.class));
    }

    @Test
    void 대중교통_경로_없음은_NONE으로_캐시한다() {
        when(odsayClient.findTransitPath(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(service.getTransitTime(1L, 2L, 9, "PUBLIC_TRANSIT"))
                .hasValueSatisfying(r -> assertThat(r.getTransportMode()).isEqualTo("NONE"));
        verify(transitCacheMapper).insert(argThat((TransitCache c) -> "NONE".equals(c.getTransportMode())));
    }

    @Test
    void 자동차_호출_실패는_예외이고_캐시하지_않는다() {
        when(tMapClient.fetchTaxiRoute(any(), any(), any(), any(), anyString(), anyInt())).thenReturn(null);

        assertThatThrownBy(() -> service.getTransitTime(1L, 2L, 9, "DRIVING"))
                .isInstanceOf(TransitLookupException.class);
        verify(transitCacheMapper, never()).insert(any(TransitCache.class));
    }

    private static Attraction attraction(String lat, String lng) {
        Attraction a = new Attraction();
        a.setLatitude(new BigDecimal(lat));
        a.setLongitude(new BigDecimal(lng));
        return a;
    }
}
