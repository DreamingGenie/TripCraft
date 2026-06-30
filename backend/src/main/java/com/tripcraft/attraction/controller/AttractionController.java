package com.tripcraft.attraction.controller;

import com.tripcraft.attraction.dto.AttractionDetailDto;
import com.tripcraft.attraction.dto.AttractionPageResponse;
import com.tripcraft.attraction.dto.RegionWithSigunguDto;
import com.tripcraft.attraction.service.AttractionService;
import com.tripcraft.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "관광지", description = "지역·카테고리별 관광지 조회·검색·상세 (공개)")
@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {

    private final AttractionService attractionService;

    @Operation(summary = "시도+시군구 지역 트리 조회")
    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<List<RegionWithSigunguDto>>> getRegions() {
        return ResponseEntity.ok(ApiResponse.ok(attractionService.getRegionsWithSigungu()));
    }

    @Operation(summary = "관광지 검색", description = "키워드·지역·시군구·카테고리 필터 + 페이지네이션")
    @GetMapping
    public ResponseEntity<ApiResponse<AttractionPageResponse>> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "sigungu", required = false) String sigungu,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal Long memberId) {

        AttractionPageResponse result = attractionService.search(keyword, region, sigungu, category, page, size, memberId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "관광지 상세 조회", description = "detailCommon/Intro/Image/Info 포함")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AttractionDetailDto>> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(attractionService.getById(id)));
    }
}
