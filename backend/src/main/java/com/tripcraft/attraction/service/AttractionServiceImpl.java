package com.tripcraft.attraction.service;

import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.dto.AttractionListItem;
import com.tripcraft.attraction.dto.AttractionPageResponse;
import com.tripcraft.attraction.mapper.AttractionMapper;
import com.tripcraft.attraction.mapper.AttractionSearchCondition;
import com.tripcraft.member.domain.Favorite;
import com.tripcraft.member.mapper.FavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttractionServiceImpl implements AttractionService {

    private final AttractionMapper attractionMapper;
    private final FavoriteMapper favoriteMapper;

    private static final Map<String, List<Integer>> REGION_CODE = Map.ofEntries(
        Map.entry("서울", List.of(1)),  Map.entry("인천", List.of(2)),
        Map.entry("대전", List.of(3)),  Map.entry("대구", List.of(4)),
        Map.entry("광주", List.of(5)),  Map.entry("부산", List.of(6)),
        Map.entry("울산", List.of(7)),  Map.entry("세종", List.of(8)),
        Map.entry("경기", List.of(31)), Map.entry("강원", List.of(32)),
        Map.entry("충북", List.of(33)), Map.entry("충남", List.of(34)),
        Map.entry("경북", List.of(35)), Map.entry("경남", List.of(36)),
        Map.entry("전북", List.of(37)), Map.entry("전남", List.of(38)),
        Map.entry("제주", List.of(39)),
        // 광역 그룹: 프론트 필터 칩과 매핑
        Map.entry("충청", List.of(33, 34)),
        Map.entry("경상", List.of(35, 36)),
        Map.entry("전라", List.of(37, 38))
    );

    private static final Map<Integer, String> CODE_REGION = Map.ofEntries(
        Map.entry(1, "서울"), Map.entry(2, "인천"), Map.entry(3, "대전"),
        Map.entry(4, "대구"), Map.entry(5, "광주"), Map.entry(6, "부산"),
        Map.entry(7, "울산"), Map.entry(8, "세종"), Map.entry(31, "경기"),
        Map.entry(32, "강원"), Map.entry(33, "충북"), Map.entry(34, "충남"),
        Map.entry(35, "경북"), Map.entry(36, "경남"), Map.entry(37, "전북"),
        Map.entry(38, "전남"), Map.entry(39, "제주")
    );

    private static final Map<String, Integer> CATEGORY_CODE = Map.of(
        "관광지", 12, "문화시설", 14, "레포츠", 28,
        "숙박", 32, "쇼핑", 38, "음식점", 39
    );

    private static final Map<Integer, String> CODE_CATEGORY = Map.of(
        12, "관광지", 14, "문화시설", 28, "레포츠",
        32, "숙박", 38, "쇼핑", 39, "음식점"
    );

    @Override
    public AttractionPageResponse search(String keyword, String region, String category,
                                         int page, int size, Long memberId) {
        List<Integer> sidoCodes = region != null && REGION_CODE.containsKey(region)
            ? REGION_CODE.get(region) : null;
        List<Integer> contentTypeIds = category != null && CATEGORY_CODE.containsKey(category)
            ? List.of(CATEGORY_CODE.get(category)) : null;

        AttractionSearchCondition cond = AttractionSearchCondition.builder()
            .keyword(keyword)
            .sidoCodes(sidoCodes)
            .contentTypeIds(contentTypeIds)
            .offset(page * size)
            .limit(size)
            .build();

        List<Attraction> attractions = attractionMapper.findByCondition(cond);
        int total = attractionMapper.countByCondition(cond);

        Set<Long> favoritedIds = Set.of();
        if (memberId != null) {
            favoritedIds = favoriteMapper.findByMemberId(memberId).stream()
                .map(Favorite::getAttractionId)
                .collect(Collectors.toSet());
        }

        final Set<Long> favIds = favoritedIds;
        List<AttractionListItem> items = attractions.stream()
            .map(a -> new AttractionListItem(
                a.getId(), a.getTitle(), a.getContentTypeId(),
                CODE_CATEGORY.getOrDefault(a.getContentTypeId(), "기타"),
                a.getSidoCode(),
                CODE_REGION.getOrDefault(a.getSidoCode(), "기타"),
                a.getAddr1(),
                a.getFirstImage(),
                a.getLatitude(), a.getLongitude(),
                favIds.contains(a.getId())
            ))
            .toList();

        return new AttractionPageResponse(items, total, page, size);
    }
}
