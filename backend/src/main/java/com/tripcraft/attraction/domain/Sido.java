package com.tripcraft.attraction.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 시도 참조 데이터.
 *
 * <p>{@code name}은 TourAPI areaCode2가 주는 공식명(동기화가 갱신, source of truth),
 * {@code alias}는 표시용 짧은 이름(사용자 수정 가능, 동기화가 건드리지 않음).
 * 표시는 alias 우선, 없으면 name.
 */
@Getter
@AllArgsConstructor
public class Sido {

    private Integer sidoCode;
    private String name;
    private String alias;
}
