package com.tripcraft.attraction.controller;

import com.tripcraft.attraction.dto.AttractionDetailDto;
import com.tripcraft.attraction.dto.AttractionPageResponse;
import com.tripcraft.attraction.dto.RegionWithSigunguDto;
import com.tripcraft.attraction.service.AttractionService;
import com.tripcraft.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {

    private final AttractionService attractionService;

    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<List<RegionWithSigunguDto>>> getRegions() {
        return ResponseEntity.ok(ApiResponse.ok(attractionService.getRegionsWithSigungu()));
    }

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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AttractionDetailDto>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(attractionService.getById(id)));
    }
}
