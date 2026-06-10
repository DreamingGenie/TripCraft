package com.tripcraft.community.mapper;

import com.tripcraft.community.domain.Post;
import com.tripcraft.community.dto.PostDetail;
import com.tripcraft.community.dto.PostListItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PostMapper {

    Optional<Post> findById(Long id);

    List<Post> findAll(int offset, int limit);

    int countAll(@Param("keyword") String keyword);

    List<PostListItem> findListItems(@Param("offset") int offset, @Param("limit") int limit,
                                     @Param("sort") String sort, @Param("keyword") String keyword);

    PostDetail findDetailById(@Param("id") Long id, @Param("memberId") Long memberId);

    void incrementViewCount(Long id);

    void incrementLikeCount(Long id);

    void decrementLikeCount(Long id);

    void insert(Post post);

    void update(Post post);

    void deleteById(Long id);
}
