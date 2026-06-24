package com.tripcraft.place.mapper;

import com.tripcraft.place.domain.MemberPlace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MemberPlaceMapper {

    List<MemberPlace> findByMemberId(Long memberId);

    Optional<MemberPlace> findById(Long id);

    void insert(MemberPlace place);

    void deleteByIdAndMember(@Param("id") Long id, @Param("memberId") Long memberId);
}
