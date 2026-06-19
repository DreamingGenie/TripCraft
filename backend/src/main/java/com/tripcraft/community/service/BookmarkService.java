package com.tripcraft.community.service;

import com.tripcraft.community.dto.PostListItem;
import com.tripcraft.community.dto.PostListPageResponse;

public interface BookmarkService {

    void toggleBookmark(Long postId, Long memberId);

    PostListPageResponse getMyBookmarks(Long memberId, int page, int size);
}
