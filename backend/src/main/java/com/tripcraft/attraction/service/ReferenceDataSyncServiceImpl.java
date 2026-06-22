package com.tripcraft.attraction.service;

import com.tripcraft.attraction.client.TourApiClient;
import com.tripcraft.attraction.client.dto.TourApiAreaItem;
import com.tripcraft.attraction.mapper.SidoMapper;
import com.tripcraft.attraction.mapper.SigunguMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 시도·시군구 참조 데이터를 TourAPI areaCode2에서 동기화한다.
 *
 * <p>name(공식명)만 upsert하고 alias(사용자 편집)는 보존한다. 외부 API 호출이 약 18건이라
 * 단일 트랜잭션으로 묶지 않고 각 upsert를 독립 커밋한다(재실행 가능·멱등).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceDataSyncServiceImpl implements ReferenceDataSyncService {

    private final TourApiClient tourApiClient;
    private final SidoMapper sidoMapper;
    private final SigunguMapper sigunguMapper;

    @Override
    public SyncResult sync() {
        long start = System.currentTimeMillis();
        int sidoCount = 0;
        int sigunguCount = 0;

        List<TourApiAreaItem> sidos = tourApiClient.fetchSidoList();
        for (TourApiAreaItem sido : sidos) {
            Integer sidoCode = parseInt(sido.getCode());
            if (sidoCode == null) continue;
            sidoMapper.upsert(sidoCode, sido.getName());
            sidoCount++;

            for (TourApiAreaItem sg : tourApiClient.fetchSigunguList(sidoCode)) {
                Integer sgCode = parseInt(sg.getCode());
                if (sgCode == null) continue;
                sigunguMapper.upsert(sidoCode, sgCode, sg.getName());
                sigunguCount++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("참조 데이터 동기화 완료: 시도 {}건, 시군구 {}건, {}ms", sidoCount, sigunguCount, elapsed);
        return new SyncResult(sidoCount, sigunguCount, elapsed);
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
