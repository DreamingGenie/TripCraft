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
import com.tripcraft.global.attach.domain.Attach;
import com.tripcraft.global.attach.mapper.AttachMapper;
import com.tripcraft.global.storage.FileStorageService;
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
    private final AttachMapper attachMapper;
    private final FileStorageService fileStorageService;

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

        // 글 작성 중 업로드된 이미지(post_draft, targetId=memberId) → 이 게시글로 연결
        attachMapper.updateTargetId("post_draft", memberId, "post", post.getId());

        return post.getId();
    }

    @Override
    @Transactional
    public PostDetail getPost(Long id, Long memberId) {
        findPostOrThrow(id);
        postMapper.incrementViewCount(id);
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
        Post post = findPostOrThrow(id);
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
        Post post = findPostOrThrow(id);
        if (!post.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // 게시글에 연결된 이미지 파일 삭제 후 attach 레코드 정리
        List<Attach> attaches = attachMapper.findByTarget("post", id);
        attaches.forEach(a -> fileStorageService.delete(a.getHostPath()));
        attachMapper.deleteByTarget("post", id);

        postMapper.softDeleteById(id);
    }

    @Override
    @Transactional
    public void toggleLike(Long id, Long memberId) {
        findPostOrThrow(id);
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

    @Override
    public PostListPageResponse getMyPosts(int page, int size, Long memberId) {
        int offset = page * size;
        var items = postMapper.findByMemberId(memberId, offset, size);
        int total = postMapper.countByMemberId(memberId);
        return new PostListPageResponse(items, total, page, size);
    }

    private Post findPostOrThrow(Long id) {
        return postMapper.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
