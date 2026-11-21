package com.tripcraft.attraction.service;

import com.tripcraft.attraction.client.KakaoLocalApiDeniedException;
import com.tripcraft.attraction.client.KakaoLocalCallLimiter;
import com.tripcraft.attraction.client.KakaoLocalCategoryClient;
import com.tripcraft.attraction.client.dto.KakaoCategoryPage;
import com.tripcraft.attraction.client.dto.KakaoPlaceItem;
import com.tripcraft.attraction.mapper.AttractionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KakaoLocalSyncServiceImplTest {

    /** 전국 격자의 첫 칸(0.5° × 0.5°). */
    static final String FIRST_CELL = "124.500000,33.000000,125.000000,33.500000";

    @Mock KakaoLocalCategoryClient client;
    @Mock KakaoLocalCallLimiter limiter;
    @Mock KakaoRegionResolver regionResolver;
    @Mock AttractionMapper attractionMapper;

    @InjectMocks KakaoLocalSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(limiter.remainingToday()).thenReturn(100_000);
        lenient().when(client.searchCategory(anyString(), anyString(), anyInt()))
                .thenReturn(KakaoCategoryPage.empty());
    }

    /** 깨지면: 키 권한이 없는데도 전국 격자를 끝까지 호출하고 "0건 수집 성공"으로 끝난다(2026-11-21 실제로 겪음). */
    @Test
    void API가_요청을_거부하면_첫_호출에서_멈추고_실패로_알린다() {
        when(client.searchCategory(anyString(), anyString(), anyInt()))
                .thenThrow(new KakaoLocalApiDeniedException("403 FORBIDDEN"));

        assertThatThrownBy(() -> service.syncByCategory("AT4"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        verify(client, times(1)).searchCategory(anyString(), anyString(), anyInt());
    }

    /** 깨지면: 45건 상한에 걸린 칸을 쪼개지 않아 밀집 지역의 46번째 이후 장소가 통째로 빠진다. */
    @Test
    void 세_페이지를_다_쓰고도_끝나지_않은_칸은_넷으로_쪼개_다시_조회한다() {
        KakaoCategoryPage full = new KakaoCategoryPage(List.of(), false, 100);
        when(client.searchCategory(eq("AT4"), eq(FIRST_CELL), anyInt())).thenReturn(full);

        service.syncByCategory("AT4");

        ArgumentCaptor<String> rects = ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).searchCategory(eq("AT4"), rects.capture(), anyInt());
        assertThat(rects.getAllValues()).contains(
                "124.500000,33.000000,124.750000,33.250000",
                "124.750000,33.000000,125.000000,33.250000",
                "124.500000,33.250000,124.750000,33.500000",
                "124.750000,33.250000,125.000000,33.500000");
    }

    /** 깨지면: 행정구역을 못 찾은 장소가 잘못된 지역 코드로 저장된다(건너뛰고 세어야 한다). */
    @Test
    void 지역을_찾지_못한_장소는_저장하지_않고_건너뛴_수로_센다() {
        KakaoPlaceItem known = new KakaoPlaceItem("1", "A", "AT4", "126.9", "37.5", "서울 종로구", null, null);
        KakaoPlaceItem unknown = new KakaoPlaceItem("2", "B", "AT4", "126.9", "37.5", "알수없는 주소", null, null);
        when(client.searchCategory(eq("AT4"), eq(FIRST_CELL), eq(1)))
                .thenReturn(new KakaoCategoryPage(List.of(known, unknown), true, 2));
        when(regionResolver.resolve("서울 종로구")).thenReturn(new int[]{1, 23});
        when(regionResolver.resolve("알수없는 주소")).thenReturn(null);

        KakaoLocalSyncService.SyncResult result = service.syncByCategory("AT4");

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }
}
