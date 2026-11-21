package com.tripcraft.plan.dto;

import lombok.Getter;

/**
 * 일정 블록 메모 저장 전용 요청 DTO.
 * 이동·리사이즈(BlockUpdateRequest)와 분리한다 → 락 경합·이벤트 의미가 명확해지고
 * version 낙관적 락과 얽히지 않는다(메모는 version 미변경).
 */
@Getter
public class BlockMemoUpdateRequest {

    private String memo;
    /**
     * 클라이언트가 편집을 시작할 때 보고 있던 메모(없으면 빈 문자열). 서버의 현재 메모가 이것과 다르면
     * 그 사이 다른 사람이 고친 것이므로 409 로 거부한다. null(구버전 클라이언트)이면 검사 없이 덮어쓴다.
     */
    private String baseMemo;
}
