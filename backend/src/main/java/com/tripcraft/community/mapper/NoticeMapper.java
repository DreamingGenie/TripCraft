package com.tripcraft.community.mapper;

import com.tripcraft.community.domain.Notice;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface NoticeMapper {

    Optional<Notice> findById(Long id);

    List<Notice> findLatest(int limit);

    void insert(Notice notice);

    void update(Notice notice);

    void deleteById(Long id);
}
