package com.tripcraft.attraction.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripcraft.attraction.client.TourApiCallLimiter;
import com.tripcraft.attraction.client.TourApiClient;
import com.tripcraft.attraction.client.dto.TourApiDetailCommonItem;
import com.tripcraft.attraction.client.dto.TourApiDetailImageItem;
import com.tripcraft.attraction.client.dto.TourApiDetailInfoItem;
import com.tripcraft.attraction.client.dto.TourApiDetailIntroItem;
import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.domain.AttractionDetailCommon;
import com.tripcraft.attraction.domain.AttractionDetailImage;
import com.tripcraft.attraction.domain.AttractionDetailInfo;
import com.tripcraft.attraction.domain.AttractionDetailIntro;
import com.tripcraft.attraction.dto.AttractionDetailDto;
import com.tripcraft.attraction.dto.AttractionGroupStat;
import com.tripcraft.attraction.dto.AttractionListItem;
import com.tripcraft.attraction.dto.AttractionPageResponse;
import com.tripcraft.attraction.dto.RegionWithSigunguDto;
import com.tripcraft.attraction.dto.SigunguItem;
import com.tripcraft.attraction.mapper.AttractionDetailCommonMapper;
import com.tripcraft.attraction.mapper.AttractionDetailImageMapper;
import com.tripcraft.attraction.mapper.AttractionDetailInfoMapper;
import com.tripcraft.attraction.mapper.AttractionDetailIntroMapper;
import com.tripcraft.attraction.mapper.AttractionMapper;
import com.tripcraft.attraction.mapper.AttractionSearchCondition;
import com.tripcraft.attraction.mapper.GroupStatRow;
import com.tripcraft.attraction.mapper.SigunguMapper;
import com.tripcraft.attraction.mapper.SigunguPair;
import com.tripcraft.member.domain.Favorite;
import com.tripcraft.member.mapper.FavoriteMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttractionServiceImpl implements AttractionService {

    private final AttractionMapper attractionMapper;
    private final FavoriteMapper favoriteMapper;
    private final SigunguMapper sigunguMapper;
    private final TourApiClient tourApiClient;
    private final ObjectMapper objectMapper;
    private final AttractionDetailCommonMapper detailCommonMapper;
    private final AttractionDetailIntroMapper  detailIntroMapper;
    private final AttractionDetailImageMapper  detailImageMapper;
    private final AttractionDetailInfoMapper   detailInfoMapper;
    private final TourApiCallLimiter           limiter;

    // sido_code → (sigungu_code → name)
    private final Map<Integer, Map<Integer, String>> sigunguCache = new ConcurrentHashMap<>();

    @PostConstruct
    void loadSigunguCache() {
        sigunguMapper.findAll().forEach(sg ->
            sigunguCache.computeIfAbsent(sg.getSidoCode(), k -> new LinkedHashMap<>())
                        .put(sg.getSigunguCode(), sg.getName()));
    }

    private static final Map<String, List<Integer>> REGION_CODE = Map.ofEntries(
        Map.entry("서울", List.of(1)),  Map.entry("인천", List.of(2)),
        Map.entry("대전", List.of(3)),  Map.entry("대구", List.of(4)),
        Map.entry("광주", List.of(5)),  Map.entry("부산", List.of(6)),
        Map.entry("울산", List.of(7)),  Map.entry("세종", List.of(8)),
        Map.entry("경기", List.of(31)), Map.entry("강원", List.of(32)),
        Map.entry("충북", List.of(33)), Map.entry("충남", List.of(34)),
        Map.entry("경북", List.of(35)), Map.entry("경남", List.of(36)),
        Map.entry("전북", List.of(37)), Map.entry("전남", List.of(38)),
        Map.entry("제주", List.of(39))
    );

    // 단일 시도 코드 → 이름 (광역 그룹 제외)
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
    public AttractionPageResponse search(String keyword, String region, String sigungu,
                                          String category, int page, int size, Long memberId) {
        List<Integer> sidoCodes = null;
        if (region != null && !region.isBlank()) {
            sidoCodes = Arrays.stream(region.split(","))
                .map(String::trim)
                .filter(REGION_CODE::containsKey)
                .flatMap(r -> REGION_CODE.get(r).stream())
                .distinct()
                .collect(Collectors.toList());
            if (sidoCodes.isEmpty()) sidoCodes = null;
        }
        List<SigunguPair> sigunguPairs = null;
        if (sigungu != null && !sigungu.isBlank()) {
            sigunguPairs = Arrays.stream(sigungu.split(","))
                .map(String::trim)
                .filter(s -> s.contains(":"))
                .map(s -> {
                    String[] parts = s.split(":", 2);
                    try {
                        return new SigunguPair(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(p -> p != null)
                .collect(Collectors.toList());
            if (sigunguPairs.isEmpty()) sigunguPairs = null;
        }
        List<Integer> contentTypeIds = null;
        if (category != null && !category.isBlank()) {
            contentTypeIds = Arrays.stream(category.split(","))
                .map(String::trim)
                .filter(CATEGORY_CODE::containsKey)
                .map(CATEGORY_CODE::get)
                .collect(Collectors.toList());
            if (contentTypeIds.isEmpty()) contentTypeIds = null;
        }

        AttractionSearchCondition cond = AttractionSearchCondition.builder()
            .keyword(keyword)
            .sidoCodes(sidoCodes)
            .sigunguPairs(sigunguPairs)
            .contentTypeIds(contentTypeIds)
            .offset(page * size)
            .limit(size)
            .build();

        List<Attraction> attractions = attractionMapper.findByCondition(cond);
        int total = attractionMapper.countByCondition(cond);
        List<GroupStatRow> rawStats = attractionMapper.findGroupStats(cond);

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
                a.getSigunguCode(),
                getSigunguName(a.getSidoCode(), a.getSigunguCode()),
                a.getAddr1(),
                a.getFirstImage(),
                a.getLatitude(), a.getLongitude(),
                favIds.contains(a.getId())
            ))
            .toList();

        List<AttractionGroupStat> groupStats = rawStats.stream()
            .map(r -> new AttractionGroupStat(
                CODE_REGION.getOrDefault(r.getSidoCode(), "기타"),
                getSigunguName(r.getSidoCode(), r.getSigunguCode()),
                CODE_CATEGORY.getOrDefault(r.getContentTypeId(), "기타"),
                r.getCount()
            ))
            .toList();

        return new AttractionPageResponse(items, total, page, size, groupStats);
    }

    @Override
    public List<RegionWithSigunguDto> getRegionsWithSigungu() {
        List<RegionWithSigunguDto> result = new ArrayList<>();
        CODE_REGION.forEach((code, sido) -> {
            Map<Integer, String> sgMap = sigunguCache.getOrDefault(code, Map.of());
            List<SigunguItem> sgList = sgMap.entrySet().stream()
                .map(e -> new SigunguItem(code, e.getKey(), e.getValue()))
                .collect(Collectors.toList());
            result.add(new RegionWithSigunguDto(sido, code, sgList));
        });
        result.sort((a, b) -> Integer.compare(a.sidoCode(), b.sidoCode()));
        return result;
    }

    @Override
    public String getSigunguName(Integer sidoCode, Integer sigunguCode) {
        if (sidoCode == null || sigunguCode == null) return "";
        return sigunguCache.getOrDefault(sidoCode, Map.of()).getOrDefault(sigunguCode, "");
    }

    @Override
    public AttractionDetailDto getById(Long id) {
        Attraction a = attractionMapper.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "관광지를 찾을 수 없습니다."));

        // detail_common 없으면 4개 API 전부 실시간 조회
        AttractionDetailCommon common = detailCommonMapper.findByContentId(a.getContentId()).orElse(null);
        if (common == null && limiter.remainingToday() >= 4) {
            fetchAndSaveDetailRealtime(a);
            common = detailCommonMapper.findByContentId(a.getContentId()).orElse(null);
        }

        AttractionDetailIntro intro = detailIntroMapper.findByContentId(a.getContentId()).orElse(null);
        List<AttractionDetailImage> images = detailImageMapper.findByContentId(a.getContentId());
        List<AttractionDetailInfo> infoList = detailInfoMapper.findByContentId(a.getContentId());

        Map<String, String> introMap = null;
        if (intro != null && intro.getIntroData() != null) {
            try {
                introMap = objectMapper.readValue(intro.getIntroData(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("introData JSON 파싱 실패 contentId={}", a.getContentId());
            }
        }

        List<AttractionDetailDto.ImageItem> imageItems = images.stream()
            .map(img -> AttractionDetailDto.ImageItem.builder()
                .originimgurl(img.getOriginimgurl())
                .smallimageurl(img.getSmallimageurl())
                .imgname(img.getImgname())
                .cpyrhtDivCd(img.getCpyrhtDivCd())
                .build())
            .toList();

        List<AttractionDetailDto.InfoItem> infoItems = infoList.stream()
            .map(info -> {
                Map<String, Object> roomData = null;
                if (info.getRoomData() != null) {
                    try {
                        roomData = objectMapper.readValue(info.getRoomData(), new TypeReference<>() {});
                    } catch (Exception ignored) {}
                }
                return AttractionDetailDto.InfoItem.builder()
                    .infoname(info.getInfoname())
                    .infotext(info.getInfotext())
                    .roomData(roomData)
                    .build();
            })
            .toList();

        return AttractionDetailDto.builder()
            .id(a.getId())
            .contentId(a.getContentId())
            .title(a.getTitle())
            .contentTypeId(a.getContentTypeId())
            .category(CODE_CATEGORY.getOrDefault(a.getContentTypeId(), "기타"))
            .region(CODE_REGION.getOrDefault(a.getSidoCode(), "기타"))
            .sigunguName(getSigunguName(a.getSidoCode(), a.getSigunguCode()))
            .addr1(a.getAddr1())
            .addr2(a.getAddr2())
            .tel(a.getTel())
            .firstImage(a.getFirstImage())
            .firstImage2(a.getFirstImage2())
            .latitude(a.getLatitude())
            .longitude(a.getLongitude())
            .overview(common != null ? common.getOverview() : null)
            .homepage(common != null ? common.getHomepage() : null)
            .telname(common != null ? common.getTelname() : null)
            .intro(introMap)
            .images(imageItems)
            .infoList(infoItems)
            .build();
    }

    private void fetchAndSaveDetailRealtime(Attraction a) {
        String contentId = a.getContentId();
        int contentTypeId = a.getContentTypeId();
        try {
            // 1. detailCommon2
            TourApiDetailCommonItem common = tourApiClient.fetchDetailCommon(contentId);
            if (common != null) {
                AttractionDetailCommon dc = new AttractionDetailCommon();
                dc.setContentId(contentId);
                dc.setOverview(common.getOverview());
                dc.setHomepage(common.getHomepage());
                dc.setTelname(common.getTelname());
                detailCommonMapper.upsert(dc);
            }
            // 2. detailIntro2
            TourApiDetailIntroItem introItem = tourApiClient.fetchDetailIntro(contentId, contentTypeId);
            if (introItem != null) {
                Map<String, Object> introMap = objectMapper.convertValue(introItem, new TypeReference<>() {});
                introMap.remove("contentid");
                introMap.remove("contenttypeid");
                introMap.values().removeIf(v -> v == null || "".equals(v));
                AttractionDetailIntro di = new AttractionDetailIntro();
                di.setContentId(contentId);
                di.setContentTypeId(contentTypeId);
                di.setIntroData(introMap.isEmpty() ? null : objectMapper.writeValueAsString(introMap));
                detailIntroMapper.upsert(di);
            }
            // 3. detailImage2
            List<TourApiDetailImageItem> imgItems = tourApiClient.fetchDetailImage(contentId);
            if (!imgItems.isEmpty()) {
                detailImageMapper.deleteByContentId(contentId);
                List<AttractionDetailImage> imgList = imgItems.stream().<AttractionDetailImage>map(img -> {
                    AttractionDetailImage item = new AttractionDetailImage();
                    item.setSerialnum(img.getSerialnum());
                    item.setOriginimgurl(img.getOriginimgurl());
                    item.setSmallimageurl(img.getSmallimageurl());
                    item.setImgname(img.getImgname());
                    item.setCpyrhtDivCd(img.getCpyrhtDivCd());
                    return item;
                }).toList();
                detailImageMapper.insertAll(contentId, imgList);
            }
            // 4. detailInfo2
            List<TourApiDetailInfoItem> infoItems = tourApiClient.fetchDetailInfo(contentId, contentTypeId);
            if (!infoItems.isEmpty()) {
                detailInfoMapper.deleteByContentId(contentId);
                List<AttractionDetailInfo> infoList = infoItems.stream().<AttractionDetailInfo>map(info -> {
                    AttractionDetailInfo item = new AttractionDetailInfo();
                    item.setSerialnum(info.getSerialnum());
                    item.setFldgubun(info.getFldgubun());
                    item.setInfoname(info.getInfoname());
                    item.setInfotext(info.getInfotext());
                    item.setSubcontentid(info.getSubcontentid());
                    item.setSubdetailalt(info.getSubdetailalt());
                    item.setSubdetailimg(info.getSubdetailimg());
                    item.setSubdetailoverview(info.getSubdetailoverview());
                    item.setSubname(info.getSubname());
                    item.setSubnum(info.getSubnum());
                    return item;
                }).toList();
                detailInfoMapper.insertAll(contentId, infoList);
            }
        } catch (Exception e) {
            log.warn("실시간 detail 조회 실패 contentId={}: {}", contentId, e.getMessage());
        }
    }
}
