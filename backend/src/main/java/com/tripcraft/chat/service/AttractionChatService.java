package com.tripcraft.chat.service;

import com.tripcraft.chat.dto.AttractionChatResponse;

public interface AttractionChatService {

    /**
     * 특정 관광지 정보를 컨텍스트로 주입해 사용자 질문에 답한다.
     *
     * @param attractionId   대상 관광지 ID
     * @param message        사용자 질문
     * @param conversationId 멀티턴 대화 식별자(없으면 신규 발급)
     */
    AttractionChatResponse chat(Long attractionId, String message, String conversationId);
}
