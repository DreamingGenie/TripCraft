package com.tripcraft.attraction.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TourAPI contentTypeId 고정 enum.
 *
 * <p>TourAPI는 콘텐츠 타입 목록을 주는 엔드포인트가 없고 문서상 고정값이므로
 * 코드↔이름 매핑의 단일 출처로 사용한다. (시도·시군구와 달리 DB 동기화 대상 아님)
 */
public enum ContentType {

    TOURISM(12, "관광지"),
    CULTURE(14, "문화시설"),
    LEISURE(28, "레포츠"),
    LODGING(32, "숙박"),
    SHOPPING(38, "쇼핑"),
    RESTAURANT(39, "음식점");

    private final int code;
    private final String label;

    ContentType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() { return code; }
    public String label() { return label; }

    private static final Map<Integer, ContentType> BY_CODE =
        Arrays.stream(values()).collect(Collectors.toMap(c -> c.code, c -> c));
    private static final Map<String, ContentType> BY_LABEL =
        Arrays.stream(values()).collect(Collectors.toMap(c -> c.label, c -> c));

    /** 코드 → 이름, 없으면 "기타" */
    public static String labelOf(Integer code) {
        if (code == null) return "기타";
        ContentType ct = BY_CODE.get(code);
        return ct != null ? ct.label : "기타";
    }

    /** 이름 → 코드, 없으면 null */
    public static Integer codeOf(String label) {
        ContentType ct = BY_LABEL.get(label);
        return ct != null ? ct.code : null;
    }

    /** 전체 이름 목록 */
    public static List<String> labels() {
        return Arrays.stream(values()).map(c -> c.label).collect(Collectors.toList());
    }
}
