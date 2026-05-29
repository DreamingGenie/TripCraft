package com.tripcraft.attraction.mapper;

import com.tripcraft.attraction.domain.Sigungu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SigunguMapper {

    List<Sigungu> findAll();
    List<Sigungu> findBySidoCode(int sidoCode);
}
