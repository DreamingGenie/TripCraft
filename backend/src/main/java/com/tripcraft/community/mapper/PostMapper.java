package com.tripcraft.community.mapper;

import com.tripcraft.community.domain.Post;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PostMapper {

    Optional<Post> findById(Long id);

    List<Post> findAll(int offset, int limit);

    int countAll();

    void incrementViewCount(Long id);

    void insert(Post post);

    void update(Post post);

    void deleteById(Long id);
}
