package com.tripcraft.community.mapper;

import com.tripcraft.community.domain.PostComment;
import com.tripcraft.community.dto.CommentItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PostCommentMapper {

    List<CommentItem> findByPostId(@Param("postId") Long postId, @Param("memberId") Long memberId);

    Optional<PostComment> findById(Long id);

    void insert(PostComment comment);

    void deleteById(Long id);
}
