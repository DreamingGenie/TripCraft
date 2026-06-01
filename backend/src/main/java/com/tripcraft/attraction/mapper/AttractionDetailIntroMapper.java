package com.tripcraft.attraction.mapper;

import com.tripcraft.attraction.domain.AttractionDetailIntro;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Optional;

@Mapper
public interface AttractionDetailIntroMapper {
    Optional<AttractionDetailIntro> findByContentId(@Param("contentId") String contentId);
    void upsert(AttractionDetailIntro intro);
}
