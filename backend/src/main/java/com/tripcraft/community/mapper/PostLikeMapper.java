package com.tripcraft.community.mapper;

import com.tripcraft.community.domain.PostLike;
import com.tripcraft.community.dto.PostListItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PostLikeMapper {

    Optional<PostLike> findByPostIdAndMemberId(Long postId, Long memberId);

    int countByPostId(Long postId);

    void insert(PostLike postLike);

    void deleteById(Long id);

    void deleteByMemberId(Long memberId);

    List<PostListItem> findByMemberId(@Param("memberId") Long memberId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    int countByMemberId(@Param("memberId") Long memberId);
}
