package com.tripcraft.community.service;

import com.tripcraft.community.dto.PostListPageResponse;

public interface LikeService {

    PostListPageResponse getMyLikes(Long memberId, int page, int size);
}
