package com.tripcraft.place.controller;

import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.place.domain.MemberPlace;
import com.tripcraft.place.dto.MemberPlaceRequest;
import com.tripcraft.place.service.MyPlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "내 장소", description = "사용자 커스텀 장소 등록·조회·삭제")
@RestController
@RequestMapping("/api/my-places")
@RequiredArgsConstructor
public class MyPlaceController {

    private final MyPlaceService myPlaceService;

    @Operation(summary = "내 커스텀 장소 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MemberPlace>>> list(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(myPlaceService.list(memberId)));
    }

    @Operation(summary = "내 커스텀 장소 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@RequestBody MemberPlaceRequest req,
                                                     @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(myPlaceService.create(req, memberId)));
    }

    @Operation(summary = "내 커스텀 장소 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") Long id,
                                                    @AuthenticationPrincipal Long memberId) {
        myPlaceService.delete(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
