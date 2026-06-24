package com.tripcraft.place.service;

import com.tripcraft.place.domain.MemberPlace;
import com.tripcraft.place.dto.MemberPlaceRequest;

import java.util.List;

public interface MyPlaceService {

    List<MemberPlace> list(Long memberId);

    Long create(MemberPlaceRequest req, Long memberId);

    void delete(Long id, Long memberId);
}
