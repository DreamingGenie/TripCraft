package com.tripcraft.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 관광지 챗봇 요청.
 * conversationId가 비어 있으면 서버가 새로 발급하여 응답으로 돌려준다(멀티턴 시작).
 */
public record AttractionChatRequest(
        @NotBlank(message = "메시지를 입력해 주세요.") String message,
        String conversationId
) {}
