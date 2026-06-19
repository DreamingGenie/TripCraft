package com.tripcraft.chat.dto;

import com.tripcraft.attraction.dto.NearbyAttraction;

import java.util.List;

/**
 * 관광지 챗봇 응답.
 * conversationId는 다음 턴에 그대로 다시 보내면 대화가 이어진다.
 * nearby는 프롬프트에 주입한 주변 장소 목록(프론트에서 지도 핀/상세 이동 버튼으로 사용).
 */
public record AttractionChatResponse(
        String reply,
        String conversationId,
        List<NearbyAttraction> nearby
) {}
