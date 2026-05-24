package com.tripcraft.attraction.mapper;

import com.tripcraft.attraction.domain.Attraction;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AttractionMapper {

    Optional<Attraction> findById(Long id);

    List<Attraction> findAll();

    List<Attraction> findByCondition(AttractionSearchCondition condition);

    int countByCondition(AttractionSearchCondition condition);

    void insertAll(List<Attraction> attractions);

    void update(Attraction attraction);
}
