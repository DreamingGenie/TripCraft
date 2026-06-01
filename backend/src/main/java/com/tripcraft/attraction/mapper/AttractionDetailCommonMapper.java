package com.tripcraft.attraction.mapper;

import com.tripcraft.attraction.domain.AttractionDetailCommon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Optional;

@Mapper
public interface AttractionDetailCommonMapper {
    Optional<AttractionDetailCommon> findByContentId(@Param("contentId") String contentId);
    void upsert(AttractionDetailCommon common);
}
