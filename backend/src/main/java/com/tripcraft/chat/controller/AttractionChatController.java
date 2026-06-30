package com.tripcraft.chat.controller;

import com.tripcraft.chat.dto.AttractionChatRequest;
import com.tripcraft.chat.dto.AttractionChatResponse;
import com.tripcraft.chat.service.AttractionChatService;
import com.tripcraft.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관광지 챗봇. 해당 관광지 정보를 컨텍스트로 주입해 자연스러운 멀티턴 Q&A를 제공한다.
 * AI 토큰을 소비하므로 인증이 필요하다(SecurityConfig: GET만 공개).
 */
@Tag(name = "관광지 AI 챗봇", description = "관광지 컨텍스트 멀티턴 Q&A (Spring AI)")
@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionChatController {

    private final AttractionChatService attractionChatService;

    @Operation(summary = "관광지 챗봇 질의", description = "관광지 정보를 컨텍스트로 주입한 멀티턴 대화")
    @PostMapping("/{id}/chat")
    public ResponseEntity<ApiResponse<AttractionChatResponse>> chat(
            @PathVariable("id") Long id,
            @Valid @RequestBody AttractionChatRequest request) {
        AttractionChatResponse response =
                attractionChatService.chat(id, request.message(), request.conversationId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
