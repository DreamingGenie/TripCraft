package com.tripcraft.place.service;

import com.tripcraft.place.domain.MemberPlace;
import com.tripcraft.place.dto.MemberPlaceRequest;
import com.tripcraft.place.mapper.MemberPlaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MyPlaceServiceImpl implements MyPlaceService {

    private final MemberPlaceMapper memberPlaceMapper;

    @Override
    public List<MemberPlace> list(Long memberId) {
        return memberPlaceMapper.findByMemberId(memberId);
    }

    @Override
    @Transactional
    public Long create(MemberPlaceRequest req, Long memberId) {
        if (req.getName() == null || req.getName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장소명이 필요합니다.");
        if (req.getLatitude() != null && req.getLongitude() != null
                && memberPlaceMapper.existsByCoords(memberId, req.getLatitude(), req.getLongitude())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록한 내 장소예요.");
        }
        MemberPlace p = new MemberPlace();
        p.setMemberId(memberId);
        p.setName(req.getName());
        p.setCategory(req.getCategory() != null ? req.getCategory() : "관광지");
        p.setAddress(req.getAddress());
        p.setLatitude(req.getLatitude());
        p.setLongitude(req.getLongitude());
        memberPlaceMapper.insert(p);
        return p.getId();
    }

    @Override
    @Transactional
    public void delete(Long id, Long memberId) {
        memberPlaceMapper.deleteByIdAndMember(id, memberId);
    }
}
