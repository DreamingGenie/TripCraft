package com.tripcraft.attraction.mapper;

import com.tripcraft.attraction.domain.Sido;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SidoMapper {

    List<Sido> findAll();

    /** 공식명(name)만 upsert. alias는 보존(사용자 편집 유지). */
    void upsert(@Param("sidoCode") int sidoCode, @Param("name") String name);
}
