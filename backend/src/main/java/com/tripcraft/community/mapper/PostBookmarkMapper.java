package com.tripcraft.community.mapper;

import com.tripcraft.community.dto.PostListItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PostBookmarkMapper {

    Optional<Long> findByPostIdAndMemberId(@Param("postId") Long postId,
                                           @Param("memberId") Long memberId);

    void insert(@Param("postId") Long postId, @Param("memberId") Long memberId);

    void delete(@Param("postId") Long postId, @Param("memberId") Long memberId);

    List<PostListItem> findByMemberId(@Param("memberId") Long memberId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    int countByMemberId(@Param("memberId") Long memberId);
}
