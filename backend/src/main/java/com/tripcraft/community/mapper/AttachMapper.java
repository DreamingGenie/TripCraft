package com.tripcraft.community.mapper;

import com.tripcraft.community.domain.Attach;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttachMapper {

    void insert(Attach attach);

    /** 게시글 등록 시 post_draft → post 로 갱신 */
    void linkToPost(@Param("postId") Long postId, @Param("names") List<String> names);

    /** 특정 게시글에 연결된 첨부 파일명 목록 조회 (삭제 시 사용) */
    List<String> findNamesByPost(@Param("postId") Long postId);

    /** 특정 게시글의 attach 레코드 전체 삭제 */
    void deleteByPost(@Param("postId") Long postId);

    /** 24시간 이상 지난 post_draft 파일명 목록 조회 (스케줄러 사용) */
    List<String> findExpiredDraftNames();

    /** 24시간 이상 지난 post_draft 레코드 삭제 */
    void deleteExpiredDrafts();
}
