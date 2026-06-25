package com.tripcraft.member.mapper;

import com.tripcraft.member.dto.RegionMapItem;
import com.tripcraft.member.dto.RegionStoryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 마이페이지 방문 지도 — 후기 기반 지역 상태/표지 사진/crop. */
@Mapper
public interface MemberMapMapper {

    /** 시도별 상태(방문/예정) + 표지 사진(사본 우선, 없으면 최신 후기) + crop + pinned + 후기 수. */
    List<RegionMapItem> findRegionStatuses(@Param("memberId") Long memberId);

    /** 한 시도에서 표지로 고를 수 있는 여행이야기(글) 목록 — 날짜·사진 수 포함. */
    List<RegionStoryItem> findRegionStories(@Param("memberId") Long memberId,
                                            @Param("sidoCode") int sidoCode);

    /** 한 글의 content(본문 이미지 추출용) — 내 글·해당 시도 검증. 아니면 null. */
    String findPostContent(@Param("memberId") Long memberId,
                           @Param("sidoCode") int sidoCode,
                           @Param("postId") long postId);

    /** 이미지(파일명)가 내 글(해당 시도)의 커버 또는 본문에 속하는지 검증 → 출처 postId. 없으면 null. */
    Long findPostIdByImage(@Param("memberId") Long memberId,
                           @Param("sidoCode") int sidoCode,
                           @Param("imageName") String imageName);

    /** 기존 표지 사본 파일 경로(교체/삭제 시 정리용). 없으면 null. */
    String findCoverHostPath(@Param("memberId") Long memberId,
                             @Param("regionLevel") String regionLevel,
                             @Param("regionCode") int regionCode);

    void upsertCover(@Param("memberId") Long memberId,
                     @Param("regionLevel") String regionLevel,
                     @Param("regionCode") int regionCode,
                     @Param("imageUrl") String imageUrl,
                     @Param("hostPath") String hostPath,
                     @Param("sourcePostId") Long sourcePostId,
                     @Param("focusX") double focusX,
                     @Param("focusY") double focusY,
                     @Param("zoom") double zoom);

    int updateCrop(@Param("memberId") Long memberId,
                   @Param("regionLevel") String regionLevel,
                   @Param("regionCode") int regionCode,
                   @Param("focusX") double focusX,
                   @Param("focusY") double focusY,
                   @Param("zoom") double zoom);

    void deleteCover(@Param("memberId") Long memberId,
                     @Param("regionLevel") String regionLevel,
                     @Param("regionCode") int regionCode);
}
