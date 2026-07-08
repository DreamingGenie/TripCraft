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
}
