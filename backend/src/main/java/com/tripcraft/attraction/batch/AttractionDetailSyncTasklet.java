package com.tripcraft.attraction.batch;

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
import com.tripcraft.attraction.mapper.AttractionDetailCommonMapper;
import com.tripcraft.attraction.mapper.AttractionDetailImageMapper;
import com.tripcraft.attraction.mapper.AttractionDetailInfoMapper;
import com.tripcraft.attraction.mapper.AttractionDetailIntroMapper;
import com.tripcraft.attraction.mapper.AttractionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class AttractionDetailSyncTasklet implements Tasklet {

    private final AttractionMapper            attractionMapper;
    private final AttractionDetailCommonMapper commonMapper;
    private final AttractionDetailIntroMapper  introMapper;
    private final AttractionDetailImageMapper  imageMapper;
    private final AttractionDetailInfoMapper   infoMapper;
    private final TourApiClient               tourApiClient;
    private final TourApiCallLimiter          limiter;
    private final ObjectMapper                objectMapper;

    private static final int PAGE_SIZE = 25; // 4 calls/건 × 25 = 100 calls/batch

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        int remaining = limiter.remainingToday();
        // 4 API calls per attraction, 최소 4개 여유 없으면 중단
        int maxAttractions = remaining / 4;
        if (maxAttractions <= 0) {
            log.info("detailSync 오늘 한도 소진 (남은 호출: {})", remaining);
            return RepeatStatus.FINISHED;
        }

        int batchSize = Math.min(PAGE_SIZE, maxAttractions);
        List<Attraction> batch = attractionMapper.findWithoutDetailSync(batchSize);

        if (batch.isEmpty()) {
            log.info("detailSync 완료: 미동기화 관광지 없음");
            return RepeatStatus.FINISHED;
        }

        int processed = 0;
        for (Attraction a : batch) {
            if (limiter.remainingToday() < 4) {
                log.info("detailSync 한도 근접, 중단 (처리: {}건)", processed);
                break;
            }
            try {
                syncDetail(a);
                processed++;
            } catch (Exception e) {
                log.warn("detailSync 실패 skip contentId={}: {}", a.getContentId(), e.getMessage());
            }
        }

        log.info("detailSync 완료: {}건 처리, 남은 호출 {}회", processed, limiter.remainingToday());
        return RepeatStatus.FINISHED;
    }

    private void syncDetail(Attraction a) throws Exception {
        String contentId = a.getContentId();
        int contentTypeId = a.getContentTypeId();

        // 1. detailCommon2
        TourApiDetailCommonItem common = tourApiClient.fetchDetailCommon(contentId);
        if (common != null) {
            AttractionDetailCommon dc = new AttractionDetailCommon();
            dc.setContentId(contentId);
            dc.setOverview(common.getOverview());
            dc.setHomepage(common.getHomepage());
            dc.setTelname(common.getTelname());
            commonMapper.upsert(dc);
        }

        // 2. detailIntro2
        TourApiDetailIntroItem intro = tourApiClient.fetchDetailIntro(contentId, contentTypeId);
        if (intro != null) {
            Map<String, Object> introMap = objectMapper.convertValue(intro, Map.class);
            introMap.remove("contentid");
            introMap.remove("contenttypeid");
            introMap.values().removeIf(v -> v == null || "".equals(v));

            AttractionDetailIntro di = new AttractionDetailIntro();
            di.setContentId(contentId);
            di.setContentTypeId(contentTypeId);
            di.setIntroData(introMap.isEmpty() ? null : objectMapper.writeValueAsString(introMap));
            introMapper.upsert(di);
        }

        // 3. detailImage2
        List<TourApiDetailImageItem> images = tourApiClient.fetchDetailImage(contentId);
        if (!images.isEmpty()) {
            imageMapper.deleteByContentId(contentId);
            List<AttractionDetailImage> imgList = images.stream().map(img -> {
                AttractionDetailImage item = new AttractionDetailImage();
                item.setSerialnum(img.getSerialnum());
                item.setOriginimgurl(img.getOriginimgurl());
                item.setSmallimageurl(img.getSmallimageurl());
                item.setImgname(img.getImgname());
                item.setCpyrhtDivCd(img.getCpyrhtDivCd());
                return item;
            }).toList();
            imageMapper.insertAll(contentId, imgList);
        }

        // 4. detailInfo2
        List<TourApiDetailInfoItem> infoItems = tourApiClient.fetchDetailInfo(contentId, contentTypeId);
        if (!infoItems.isEmpty()) {
            infoMapper.deleteByContentId(contentId);
            List<AttractionDetailInfo> infoList = infoItems.stream().map(info -> {
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
                // 숙박 room* 필드 JSON 직렬화
                if (contentTypeId == 32 && info.getRoomcode() != null) {
                    try {
                        Map<String, Object> roomMap = objectMapper.convertValue(info, Map.class);
                        // room* 필드만 추출
                        Map<String, Object> filtered = new java.util.LinkedHashMap<>();
                        roomMap.forEach((k, v) -> {
                            if (k.startsWith("room") && v != null && !"".equals(v)) filtered.put(k, v);
                        });
                        item.setRoomData(filtered.isEmpty() ? null : objectMapper.writeValueAsString(filtered));
                    } catch (Exception ignored) {}
                }
                return item;
            }).toList();
            infoMapper.insertAll(contentId, infoList);
        }
    }
}
