package com.tripcraft.community.service;

import com.tripcraft.community.dto.PostCreateRequest;
import com.tripcraft.community.dto.PostUpdateRequest;
import com.tripcraft.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글 이미지 생명주기 통합테스트 — 실 MySQL + 실제 파일.
 * 업로드 직후 draft 로 기록된 이미지가 글에 연결되는지, 파일 삭제가 DB 커밋과 어긋나지 않는지를 본다.
 */
class PostImageLifecycleIntegrationTest extends AbstractIntegrationTest {

    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired PostService postService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    @TempDir Path uploadDir;
    long memberId;

    @BeforeEach
    void setUp() {
        String email = "image-it-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
        jdbc.update("INSERT INTO member (email, nickname) VALUES (?, ?)", email, "writer");
        memberId = jdbc.queryForObject("SELECT id FROM member WHERE email = ?", Long.class, email);
    }

    /** 깨지면: 글을 쓰며 올린 본문 이미지가 draft 로 남아 24시간 뒤 청소 스케줄러에 지워진다. */
    @Test
    void 글을_등록하면_작성_중_올린_이미지가_그_글에_연결된다() throws Exception {
        uploadDraft("a.png");

        long postId = createPost();

        assertThat(count("post_draft", memberId)).isZero();
        assertThat(count("post", postId)).isEqualTo(1);
    }

    /** 깨지면: 수정 화면에서 올린 본문 이미지가 draft 로 남아 하루 뒤 삭제된다(2026-06 실제 결함의 회귀). */
    @Test
    void 글을_수정하면_수정_중_올린_이미지도_그_글에_연결된다() throws Exception {
        long postId = createPost();
        uploadDraft("b.png");

        PostUpdateRequest req = new PostUpdateRequest();
        ReflectionTestUtils.setField(req, "title", "수정한 제목");
        ReflectionTestUtils.setField(req, "content", "수정한 본문");
        postService.updatePost(postId, req, memberId);

        assertThat(count("post_draft", memberId)).isZero();
        assertThat(count("post", postId)).isEqualTo(1);
    }

    /** 깨지면: 글을 지워도 서버에 이미지 파일이 남는다. */
    @Test
    void 글_삭제가_커밋되면_이미지_파일도_지워진다() throws Exception {
        Path file = uploadDraft("c.png");
        long postId = createPost();

        postService.deletePost(postId, memberId);

        assertThat(count("post", postId)).isZero();
        assertThat(Files.exists(file)).isFalse();
    }

    /** 깨지면: 삭제 트랜잭션이 롤백됐는데 파일만 먼저 지워져, 살아 있는 글의 이미지가 깨진다. */
    @Test
    void 글_삭제가_롤백되면_이미지_파일은_남는다() throws Exception {
        Path file = uploadDraft("d.png");
        long postId = createPost();

        tx.executeWithoutResult(status -> {
            postService.deletePost(postId, memberId);
            status.setRollbackOnly();
        });

        assertThat(count("post", postId)).isEqualTo(1);
        assertThat(Files.exists(file)).isTrue();
    }

    /** 업로드 API 가 하는 일(파일 저장 + post_draft 기록)을 그대로 만든다. */
    private Path uploadDraft(String name) throws Exception {
        Path file = Files.write(uploadDir.resolve(name), new byte[]{1, 2, 3});
        jdbc.update("INSERT INTO attach (name, host_path, target, target_id) VALUES (?, ?, 'post_draft', ?)",
                name, file.toString(), memberId);
        return file;
    }

    private long createPost() {
        PostCreateRequest req = new PostCreateRequest();
        ReflectionTestUtils.setField(req, "title", "제목");
        ReflectionTestUtils.setField(req, "content", "본문");
        return postService.createPost(req, memberId);
    }

    private int count(String target, long targetId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM attach WHERE target = ? AND target_id = ?",
                Integer.class, target, targetId);
    }
}
