package com.tripcraft.community.service;

import com.tripcraft.community.dto.PostListItem;
import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.mapper.PostBookmarkMapper;
import com.tripcraft.community.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private final PostBookmarkMapper bookmarkMapper;
    private final PostMapper postMapper;

    @Override
    @Transactional
    public void toggleBookmark(Long postId, Long memberId) {
        findPostOrThrow(postId);
        bookmarkMapper.findByPostIdAndMemberId(postId, memberId).ifPresentOrElse(
                existing -> bookmarkMapper.delete(postId, memberId),
                () -> bookmarkMapper.insert(postId, memberId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PostListPageResponse getMyBookmarks(Long memberId, int page, int size) {
        List<PostListItem> items = bookmarkMapper.findByMemberId(memberId, page * size, size);
        int total = bookmarkMapper.countByMemberId(memberId);
        return new PostListPageResponse(items, total, page, size);
    }

    private void findPostOrThrow(Long postId) {
        postMapper.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."));
    }
}
