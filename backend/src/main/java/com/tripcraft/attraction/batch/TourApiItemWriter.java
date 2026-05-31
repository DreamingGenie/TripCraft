package com.tripcraft.attraction.batch;

import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.mapper.AttractionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;

/**
 * Attraction 목록을 DB에 UPSERT하는 Writer.
 *
 * <p>청크 단위(기본 100건)로 {@link AttractionMapper#insertAll}을 호출합니다.
 * SQL은 {@code ON DUPLICATE KEY UPDATE}로 작성되어 있어
 * 신규 데이터는 INSERT, 기존 데이터는 UPDATE됩니다.
 */
@Slf4j
@RequiredArgsConstructor
public class TourApiItemWriter implements ItemWriter<Attraction> {

    private final AttractionMapper attractionMapper;

    @Override
    public void write(Chunk<? extends Attraction> chunk) {
        if (chunk.isEmpty()) return;

        ArrayList<Attraction> items = new ArrayList<>(chunk.getItems());
        attractionMapper.insertAll(items);
        log.debug("attraction UPSERT 완료 — {}건", items.size());
    }
}
