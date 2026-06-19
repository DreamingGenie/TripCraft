package com.tripcraft.global.attach.mapper;

import com.tripcraft.global.attach.domain.Attach;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttachMapper {

    void insert(Attach attach);

    // post_draft → post로 target 업데이트 (글 저장 완료 시)
    void updateTargetId(@Param("oldTarget") String oldTarget,
                        @Param("oldTargetId") long oldTargetId,
                        @Param("newTarget") String newTarget,
                        @Param("newTargetId") long newTargetId);

    List<Attach> findByTarget(@Param("target") String target,
                              @Param("targetId") long targetId);

    void deleteByTarget(@Param("target") String target,
                        @Param("targetId") long targetId);

    /** 24시간 이상 지난 post_draft 레코드 조회 (고아 파일 정리 스케줄러용) */
    List<Attach> findExpiredDrafts();

    /** 24시간 이상 지난 post_draft 레코드 삭제 */
    void deleteExpiredDrafts();
}
