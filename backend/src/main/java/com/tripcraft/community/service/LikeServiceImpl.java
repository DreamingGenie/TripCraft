package com.tripcraft.community.service;

import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.mapper.PostLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final PostLikeMapper postLikeMapper;

    @Override
    @Transactional(readOnly = true)
    public PostListPageResponse getMyLikes(Long memberId, int page, int size) {
        var items = postLikeMapper.findByMemberId(memberId, page * size, size);
        int total = postLikeMapper.countByMemberId(memberId);
        return new PostListPageResponse(items, total, page, size);
    }
}
