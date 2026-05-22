package com.tripcraft.member.mapper;

import com.tripcraft.member.domain.Favorite;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FavoriteMapper {

    List<Favorite> findByMemberId(Long memberId);

    List<Favorite> findByMemberIdAndSigunguCode(Long memberId, Integer sigunguCode);

    boolean existsByMemberIdAndAttractionId(Long memberId, Long attractionId);

    void insert(Favorite favorite);

    void deleteById(Long id);

    void deleteByMemberId(Long memberId);
}
