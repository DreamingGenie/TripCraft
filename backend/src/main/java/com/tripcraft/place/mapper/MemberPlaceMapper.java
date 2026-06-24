package com.tripcraft.place.mapper;

import com.tripcraft.place.domain.MemberPlace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface MemberPlaceMapper {

    List<MemberPlace> findByMemberId(Long memberId);

    Optional<MemberPlace> findById(Long id);

    /** 같은 회원이 동일 좌표의 내 장소를 이미 등록했는지(중복 방지) */
    boolean existsByCoords(@Param("memberId") Long memberId,
                           @Param("lat") BigDecimal lat,
                           @Param("lng") BigDecimal lng);

    void insert(MemberPlace place);

    void deleteByIdAndMember(@Param("id") Long id, @Param("memberId") Long memberId);
}
