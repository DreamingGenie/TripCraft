package com.tripcraft.place.controller;

import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.place.client.KakaoLocalClient;
import com.tripcraft.place.dto.PlaceSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 장소 검색(Kakao Local). 인증 필요(로그인 기능). */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final KakaoLocalClient kakaoLocalClient;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PlaceSearchItem>>> search(
            @RequestParam("query") String query) {
        return ResponseEntity.ok(ApiResponse.ok(kakaoLocalClient.searchKeyword(query)));
    }
}
