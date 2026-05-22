package com.tripcraft.community.mapper;

import com.tripcraft.community.domain.PostLike;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface PostLikeMapper {

    Optional<PostLike> findByPostIdAndMemberId(Long postId, Long memberId);

    int countByPostId(Long postId);

    void insert(PostLike postLike);

    void deleteById(Long id);

    void deleteByMemberId(Long memberId);
}
