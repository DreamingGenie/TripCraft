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
import com.tripcraft.community.event.PostImageDeletedEvent;
import com.tripcraft.global.attach.domain.Attach;
import com.tripcraft.global.attach.mapper.AttachMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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
        // 대표사진(post_cover_draft, targetId=memberId) → 이 게시글의 커버로 연결
        attachMapper.updateTargetId("post_cover_draft", memberId, "post_cover", post.getId());

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

        // 새 대표사진을 올린 경우에만 기존 커버를 교체 (안 올렸으면 기존 커버 유지)
        if (!attachMapper.findByTarget("post_cover_draft", memberId).isEmpty()) {
            attachMapper.deleteByTarget("post_cover", id);
            attachMapper.updateTargetId("post_cover_draft", memberId, "post_cover", id);
        }
    }

    @Override
    @Transactional
    public void deletePost(Long id, Long memberId) {
        Post post = findPostOrThrow(id);
        if (!post.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // attach 레코드 먼저 삭제 후 게시글 soft delete (DB 트랜잭션 범위)
        // 파일 삭제는 커밋 성공 이후 이벤트 리스너에서 처리해 트랜잭션 롤백 시 파일이 먼저 지워지는 불일치를 방지
        List<Attach> attaches = new java.util.ArrayList<>(attachMapper.findByTarget("post", id));
        attaches.addAll(attachMapper.findByTarget("post_cover", id));
        List<String> hostPaths = attaches.stream()
                .map(Attach::getHostPath)
                .filter(p -> p != null && !p.isBlank())
                .toList();
        attachMapper.deleteByTarget("post", id);
        attachMapper.deleteByTarget("post_cover", id);
        postMapper.softDeleteById(id);

        if (!hostPaths.isEmpty()) {
            eventPublisher.publishEvent(new PostImageDeletedEvent(hostPaths));
        }
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
