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
import com.tripcraft.attraction.domain.ContentType;
import com.tripcraft.attraction.domain.AttractionDetailCommon;
import com.tripcraft.attraction.domain.AttractionDetailImage;
import com.tripcraft.attraction.domain.AttractionDetailInfo;
import com.tripcraft.attraction.domain.AttractionDetailIntro;
import com.tripcraft.attraction.dto.AttractionDetailDto;
import com.tripcraft.attraction.dto.AttractionGroupStat;
import com.tripcraft.attraction.dto.AttractionListItem;
import com.tripcraft.attraction.dto.AttractionPageResponse;
import com.tripcraft.attraction.dto.NearbyAttraction;
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

import java.math.BigDecimal;
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
    private final RegionService regionService;
    private final TourApiClient tourApiClient;
    private final ObjectMapper objectMapper;
    private final AttractionDetailCommonMapper detailCommonMapper;
    private final AttractionDetailIntroMapper  detailIntroMapper;
    private final AttractionDetailImageMapper  detailImageMapper;
    private final AttractionDetailInfoMapper   detailInfoMapper;
    private final TourApiCallLimiter           limiter;

    /** 시군구 표시명 조회 헬퍼 (없으면 빈 문자열) */
    private static String sgName(Map<Integer, Map<Integer, String>> m, Integer sido, Integer sigungu) {
        return m.getOrDefault(sido, Map.of()).getOrDefault(sigungu, "");
    }

    @Override
    public AttractionPageResponse search(String keyword, String region, String sigungu,
                                          String category, int page, int size, Long memberId) {
        Map<String, Integer> sidoCodeByLabel = regionService.sidoCodeByLabel();
        List<Integer> sidoCodes = null;
        if (region != null && !region.isBlank()) {
            sidoCodes = Arrays.stream(region.split(","))
                .map(String::trim)
                .map(sidoCodeByLabel::get)
                .filter(java.util.Objects::nonNull)
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
                .map(ContentType::codeOf)
                .filter(java.util.Objects::nonNull)
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
        Map<Integer, String> sidoLabels = regionService.sidoLabelMap();
        Map<Integer, Map<Integer, String>> sigunguLabels = regionService.sigunguLabelMap();
        List<AttractionListItem> items = attractions.stream()
            .map(a -> new AttractionListItem(
                a.getId(), a.getTitle(), a.getContentTypeId(),
                ContentType.labelOf(a.getContentTypeId()),
                a.getSidoCode(),
                sidoLabels.getOrDefault(a.getSidoCode(), "기타"),
                a.getSigunguCode(),
                sgName(sigunguLabels, a.getSidoCode(), a.getSigunguCode()),
                a.getAddr1(),
                a.getFirstImage(),
                a.getLatitude(), a.getLongitude(),
                favIds.contains(a.getId())
            ))
            .toList();

        List<AttractionGroupStat> groupStats = rawStats.stream()
            .map(r -> new AttractionGroupStat(
                sidoLabels.getOrDefault(r.getSidoCode(), "기타"),
                sgName(sigunguLabels, r.getSidoCode(), r.getSigunguCode()),
                ContentType.labelOf(r.getContentTypeId()),
                r.getCount()
            ))
            .toList();

        return new AttractionPageResponse(items, total, page, size, groupStats);
    }

    @Override
    public List<RegionWithSigunguDto> getRegionsWithSigungu() {
        return regionService.getRegionsWithSigungu();
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
            .category(ContentType.labelOf(a.getContentTypeId()))
            .region(regionService.sidoLabelMap().getOrDefault(a.getSidoCode(), "기타"))
            .sigunguName(sgName(regionService.sigunguLabelMap(), a.getSidoCode(), a.getSigunguCode()))
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

    @Override
    public List<NearbyAttraction> findNearby(BigDecimal lat, BigDecimal lng, Long excludeId, double radiusKm, int limit) {
        if (lat == null || lng == null) return List.of();
        // bounding box: 위도 1도≈111km, 경도는 cos(위도)로 보정
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat.doubleValue())));
        BigDecimal minLat = lat.subtract(BigDecimal.valueOf(latDelta));
        BigDecimal maxLat = lat.add(BigDecimal.valueOf(latDelta));
        BigDecimal minLng = lng.subtract(BigDecimal.valueOf(lngDelta));
        BigDecimal maxLng = lng.add(BigDecimal.valueOf(lngDelta));
        List<NearbyAttraction> list = attractionMapper.findNearby(lat, lng, minLat, maxLat, minLng, maxLng, excludeId, limit);
        list.forEach(n -> n.setCategory(ContentType.labelOf(n.getContentTypeId())));
        return list;
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
