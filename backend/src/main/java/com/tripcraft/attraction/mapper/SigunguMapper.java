package com.tripcraft.attraction.mapper;

import com.tripcraft.attraction.domain.Sigungu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SigunguMapper {

    List<Sigungu> findAll();
    List<Sigungu> findBySidoCode(int sidoCode);

    /** 공식명(name)만 upsert. alias는 보존(사용자 편집 유지). */
    void upsert(@Param("sidoCode") int sidoCode,
                @Param("sigunguCode") int sigunguCode,
                @Param("name") String name);
}
