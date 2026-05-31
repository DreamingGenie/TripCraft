package com.tripcraft.attraction.batch;

import com.tripcraft.attraction.client.TourApiClient;
import com.tripcraft.attraction.client.dto.TourApiItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * TourAPI를 지역 × 콘텐츠 타입 조합으로 순회하며 TourApiItem을 한 건씩 반환하는 Reader.
 *
 * <p>동작 방식:
 * <ol>
 *   <li>17개 지역 × 6개 콘텐츠 타입 = 102개 조합을 사전에 생성</li>
 *   <li>각 조합마다 TourAPI를 PAGE_SIZE 단위로 페이지 순회하며 버퍼에 적재</li>
 *   <li>{@code read()} 호출 시 버퍼에서 하나씩 꺼내 반환</li>
 *   <li>모든 조합을 소진하면 {@code null} 반환 → Spring Batch가 Step 종료로 인식</li>
 * </ol>
 *
 * <p>{@code @StepScope}로 등록되어 Step 실행마다 새 인스턴스가 생성되므로
 * 상태(버퍼, 인덱스, 페이지 번호)가 매 실행마다 초기화됩니다.
 */
@Slf4j
public class TourApiItemReader implements ItemReader<TourApiItem> {

    private static final int[] AREA_CODES     = { 1, 2, 3, 4, 5, 6, 7, 8, 31, 32, 33, 34, 35, 36, 37, 38, 39 };
    private static final int[] CONTENT_TYPES  = { 12, 14, 28, 32, 38, 39 };
    private static final int   PAGE_SIZE      = 1000;

    private final TourApiClient tourApiClient;

    /** 처리할 (areaCode, contentTypeId) 조합 목록 */
    private final List<int[]> combinations;

    /** 현재 처리 중인 조합 인덱스 */
    private int combinationIndex = 0;

    /** 현재 조합의 페이지 번호 */
    private int pageNo = 1;

    /** 현재 페이지에서 미처 반환하지 못한 아이템 버퍼 */
    private final Deque<TourApiItem> buffer = new ArrayDeque<>();

    public TourApiItemReader(TourApiClient tourApiClient) {
        this.tourApiClient = tourApiClient;
        this.combinations  = buildCombinations();
        log.info("TourApiItemReader 초기화 — 총 {}개 조합", combinations.size());
    }

    /**
     * 아이템을 한 건씩 반환합니다.
     *
     * @return 다음 TourApiItem, 모든 데이터를 소진했으면 {@code null}
     */
    @Override
    public TourApiItem read() {
        // 버퍼에 남아 있으면 바로 반환
        if (!buffer.isEmpty()) {
            return buffer.poll();
        }

        // 버퍼가 비었으면 다음 페이지 또는 다음 조합에서 채움
        while (combinationIndex < combinations.size()) {
            int[] combo       = combinations.get(combinationIndex);
            int   areaCode    = combo[0];
            int   contentType = combo[1];

            List<TourApiItem> page = tourApiClient.fetchAreaList(areaCode, contentType, pageNo, PAGE_SIZE);

            if (page.isEmpty()) {
                // 결과 없음 → 다음 조합으로 이동
                log.debug("TourAPI 결과 없음 — areaCode={} contentType={} page={}", areaCode, contentType, pageNo);
                moveToNextCombination();
                continue;
            }

            buffer.addAll(page);
            log.info("TourAPI 페이지 로드 — areaCode={} contentType={} page={} → {}건",
                    areaCode, contentType, pageNo, page.size());

            if (page.size() < PAGE_SIZE) {
                // 마지막 페이지 → 다음 조합으로 이동
                moveToNextCombination();
            } else {
                // 다음 페이지 있음
                pageNo++;
            }

            return buffer.poll();
        }

        // 모든 조합 소진
        log.info("TourApiItemReader: 모든 조합 처리 완료");
        return null;
    }

    // ───────── private ─────────

    private void moveToNextCombination() {
        combinationIndex++;
        pageNo = 1;
    }

    private static List<int[]> buildCombinations() {
        List<int[]> list = new ArrayList<>(AREA_CODES.length * CONTENT_TYPES.length);
        for (int area : AREA_CODES) {
            for (int type : CONTENT_TYPES) {
                list.add(new int[]{area, type});
            }
        }
        return list;
    }
}
