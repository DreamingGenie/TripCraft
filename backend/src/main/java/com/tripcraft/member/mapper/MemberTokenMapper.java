package com.tripcraft.member.mapper;

import com.tripcraft.member.domain.MemberToken;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface MemberTokenMapper {

    Optional<MemberToken> findByToken(String token);

    void insert(MemberToken memberToken);

    void deleteByMemberId(Long memberId);

    void deleteByToken(String token);
}
