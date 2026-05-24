package com.tripcraft.attraction.service;

import com.tripcraft.attraction.dto.AttractionPageResponse;

public interface AttractionService {

    AttractionPageResponse search(String keyword, String region, String category, int page, int size, Long memberId);
}
