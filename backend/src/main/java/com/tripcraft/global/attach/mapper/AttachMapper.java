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
}
