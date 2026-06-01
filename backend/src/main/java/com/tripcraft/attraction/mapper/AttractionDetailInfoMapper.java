package com.tripcraft.attraction.mapper;

import com.tripcraft.attraction.domain.AttractionDetailInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AttractionDetailInfoMapper {
    List<AttractionDetailInfo> findByContentId(@Param("contentId") String contentId);
    void deleteByContentId(@Param("contentId") String contentId);
    void insertAll(@Param("contentId") String contentId, @Param("list") List<AttractionDetailInfo> list);
}
