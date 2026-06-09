package com.tripcraft.community.service;

import com.tripcraft.community.domain.Post;
import com.tripcraft.community.domain.PostLike;
import com.tripcraft.community.dto.PostCreateRequest;
import com.tripcraft.community.dto.PostDetail;
import com.tripcraft.community.dto.PostListItem;
import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.dto.PostUpdateRequest;
import com.tripcraft.community.mapper.PostLikeMapper;
import com.tripcraft.community.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostLikeMapper postLikeMapper;

    @Override
    public PostListPageResponse getPosts(int page, int size, String sort, String keyword, Long memberId) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        List<PostListItem> items = postMapper.findListItems(page * size, size, sort, kw);
        int total = postMapper.countAll(kw);
        return new PostListPageResponse(items, total, page, size);
    }

    @Override
    @Transactional
    public Long createPost(PostCreateRequest req, Long memberId) {
        Post post = new Post();
        post.setMemberId(memberId);
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        post.setTripId(req.getTripId());
        postMapper.insert(post);
        return post.getId();
    }

    @Override
    @Transactional
    public PostDetail getPost(Long id, Long memberId) {
        Post post = postMapper.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        postMapper.incrementViewCount(id);
        // memberId가 null이면 0을 전달하여 liked/mine 모두 false 처리
        Long queryMemberId = memberId != null ? memberId : 0L;
        PostDetail detail = postMapper.findDetailById(id, queryMemberId);
        if (detail == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return detail;
    }

    @Override
    @Transactional
    public void updatePost(Long id, PostUpdateRequest req, Long memberId) {
        Post post = postMapper.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!post.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        postMapper.update(post);
    }

    @Override
    @Transactional
    public void deletePost(Long id, Long memberId) {
        Post post = postMapper.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!post.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        postMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void toggleLike(Long id, Long memberId) {
        postMapper.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        postLikeMapper.findByPostIdAndMemberId(id, memberId).ifPresentOrElse(
            like -> {
                postLikeMapper.deleteById(like.getId());
                postMapper.decrementLikeCount(id);
            },
            () -> {
                PostLike like = new PostLike();
                like.setPostId(id);
                like.setMemberId(memberId);
                postLikeMapper.insert(like);
                postMapper.incrementLikeCount(id);
            }
        );
    }
}
