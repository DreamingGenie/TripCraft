package com.tripcraft.member.service;

import com.tripcraft.member.dto.CoverCropRequest;
import com.tripcraft.member.dto.CoverImageRequest;
import com.tripcraft.member.dto.RegionImageItem;
import com.tripcraft.member.dto.RegionMapItem;
import com.tripcraft.member.dto.RegionStoryItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 마이페이지 방문 지도 — 후기 기반 지역 상태/표지 사진/crop. */
public interface MemberMapService {

    List<RegionMapItem> getMap(Long memberId);

    List<RegionStoryItem> getRegionStories(Long memberId, int sidoCode);

    List<RegionImageItem> getPostImages(Long memberId, int sidoCode, long postId);

    /** 후보 사진(attach)을 지도 전용으로 복사해 표지로 지정. */
    void setCoverFromImage(Long memberId, CoverImageRequest request);

    /** 새 사진을 업로드해 표지로 지정(직접 업로드). */
    void uploadCover(Long memberId, int sidoCode, MultipartFile file);

    /** 표지 crop(초점/확대)만 갱신. */
    void updateCrop(Long memberId, CoverCropRequest request);

    /** 표지 해제 → 기본값(최신 후기)으로 복귀 + 사본 파일 정리. */
    void resetCover(Long memberId, int sidoCode);
}
