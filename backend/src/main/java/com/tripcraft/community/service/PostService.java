package com.tripcraft.community.service;

import com.tripcraft.community.dto.PostCreateRequest;
import com.tripcraft.community.dto.PostDetail;
import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.dto.PostUpdateRequest;

public interface PostService {

    PostListPageResponse getPosts(int page, int size, String sort, String keyword, Long memberId);

    Long createPost(PostCreateRequest req, Long memberId);

    PostDetail getPost(Long id, Long memberId);

    void updatePost(Long id, PostUpdateRequest req, Long memberId);

    void deletePost(Long id, Long memberId);

    void toggleLike(Long id, Long memberId);
}
