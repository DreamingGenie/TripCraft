package com.tripcraft.attraction.service;

import com.tripcraft.attraction.domain.Sigungu;
import com.tripcraft.attraction.mapper.SigunguMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카카오 로컬 응답의 {@code address_name} 문자열을 TripCraft의
 * {@code sido_code}/{@code sigungu_code}(TourAPI areaCode2 체계)로 변환한다.
 *
 * <p>카카오 시도 표기(예: "충북", "경북")는 TourAPI 공식명("충청북도" 등)과 달라
 * 명시적 별칭 맵으로 시도 코드를 결정하고, 시군구는 해당 시도의 {@code sigungu} 참조 테이블
 * 명칭과 이름 매칭한다. 매칭 실패 시 {@code null}을 반환하며 호출부가 건수를 집계한다.
 *
 * <p>Phase A(개선 02)의 best-effort 매핑이다. 좌표 역지오코딩 보완은 후속 과제.
 */
@Slf4j
@Component
public class KakaoRegionResolver {

    private final SigunguMapper sigunguMapper;

    /** 카카오 region1 표기 → TourAPI areaCode2 시도 코드. 축약/공식 표기 모두 수록. */
    private static final Map<String, Integer> SIDO_CODE = new HashMap<>();
    static {
        put(1,  "서울", "서울특별시");
        put(2,  "인천", "인천광역시");
        put(3,  "대전", "대전광역시");
        put(4,  "대구", "대구광역시");
        put(5,  "광주", "광주광역시");
        put(6,  "부산", "부산광역시");
        put(7,  "울산", "울산광역시");
        put(8,  "세종", "세종특별자치시", "세종시");
        put(31, "경기", "경기도");
        put(32, "강원", "강원도", "강원특별자치도");
        put(33, "충북", "충청북도");
        put(34, "충남", "충청남도");
        put(35, "경북", "경상북도");
        put(36, "경남", "경상남도");
        put(37, "전북", "전라북도", "전북특별자치도");
        put(38, "전남", "전라남도");
        put(39, "제주", "제주도", "제주특별자치도");
    }
    private static void put(int code, String... names) {
        for (String n : names) SIDO_CODE.put(n, code);
    }

    /** (sidoCode, 공백제거 시군구명) → sigunguCode */
    private final Map<String, Integer> sigunguByName = new HashMap<>();

    public KakaoRegionResolver(SigunguMapper sigunguMapper) {
        this.sigunguMapper = sigunguMapper;
    }

    /** 수집 실행 시작 시 1회 호출해 시군구 참조 테이블을 메모리에 적재한다. */
    public void refresh() {
        sigunguByName.clear();
        List<Sigungu> all = sigunguMapper.findAll();
        for (Sigungu sg : all) {
            if (sg.getName() == null) continue;
            sigunguByName.put(key(sg.getSidoCode(), sg.getName()), sg.getSigunguCode());
        }
        log.info("KakaoRegionResolver: 시군구 {}건 로드", sigunguByName.size());
    }

    /**
     * 카카오 address_name → [sidoCode, sigunguCode]. 매칭 실패 시 null.
     * 예: "서울 강남구 역삼동 826-21", "경기 성남시 분당구 정자동 …"
     */
    public int[] resolve(String addressName) {
        if (addressName == null || addressName.isBlank()) return null;
        String[] t = addressName.trim().split("\\s+");
        if (t.length < 2) return null;

        Integer sido = SIDO_CODE.get(t[0]);
        if (sido == null) return null;

        // 시군구 후보: region2 단독, region2+region3 결합("성남시"+"분당구") 순으로 시도.
        Integer sgg = sigunguByName.get(key(sido, t[1]));
        if (sgg == null && t.length >= 3) {
            sgg = sigunguByName.get(key(sido, t[1] + t[2]));
        }
        if (sgg == null) return null;

        return new int[]{ sido, sgg };
    }

    private String key(int sidoCode, String sigunguName) {
        return sidoCode + ":" + sigunguName.replaceAll("\\s+", "");
    }
}
