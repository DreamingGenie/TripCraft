package com.tripcraft.member.mapper;

import com.tripcraft.member.domain.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface MemberMapper {

    Optional<Member> findById(Long id);

    Optional<Member> findByEmail(String email);

    Optional<Member> findByNickname(String nickname);

    Optional<Member> findBySocial(@Param("provider") String provider, @Param("socialId") String socialId);

    void insert(Member member);

    /** 기존 회원에 소셜 계정 연결 */
    void linkSocial(@Param("id") Long id, @Param("provider") String provider, @Param("socialId") String socialId);

    void deleteById(Long id);

    void updateNickname(@Param("id") Long id, @Param("nickname") String nickname);

    void updatePassword(@Param("id") Long id, @Param("password") String password);
}
