package com.tripcraft.member.mapper;

import com.tripcraft.member.domain.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface MemberMapper {

    Optional<Member> findById(Long id);

    Optional<Member> findByEmail(String email);

    void insert(Member member);

    void deleteById(Long id);

    void updateNickname(@Param("id") Long id, @Param("nickname") String nickname);

    void updatePassword(@Param("id") Long id, @Param("password") String password);
}
