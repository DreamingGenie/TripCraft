package com.tripcraft.community.service;

import com.tripcraft.community.dto.PostCreateRequest;
import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.plan.dto.CollaboratorItem;
import com.tripcraft.plan.service.TripService;
import com.tripcraft.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작성자·협업자 프로필 이미지를 붙이는 조인이 결과 행 수를 바꾸지 않는지 실 MySQL 로 확인한다.
 */
class PostListIntegrationTest extends AbstractIntegrationTest {

    @Autowired PostService postService;
    @Autowired TripService tripService;
    @Autowired JdbcTemplate jdbc;

    long memberId;
    String marker;

    @BeforeEach
    void setUp() {
        marker = "plist" + System.nanoTime();
        jdbc.update("INSERT INTO member (email, nickname) VALUES (?, 'writer')", marker + "@test.local");
        memberId = jdbc.queryForObject("SELECT id FROM member WHERE email = ?", Long.class, marker + "@test.local");
        // 프로필 이미지 행이 두 장(교체 과정에서 옛 행이 남은 상황)
        jdbc.update("INSERT INTO attach (name, target, target_id) VALUES ('old.png', 'profile', ?)", memberId);
        jdbc.update("INSERT INTO attach (name, target, target_id) VALUES ('new.png', 'profile', ?)", memberId);
    }

    /** 깨지면: 작성자의 프로필 이미지 행이 둘이면 글 목록에 같은 글이 두 번 나온다(조인이 행을 늘림). */
    @Test
    void 프로필_이미지가_두_장이어도_글은_한_번만_나오고_최신_이미지가_붙는다() {
        PostCreateRequest req = new PostCreateRequest();
        ReflectionTestUtils.setField(req, "title", marker);
        ReflectionTestUtils.setField(req, "content", "본문");
        postService.createPost(req, memberId);

        PostListPageResponse page = postService.getPosts(0, 10, null, marker, null);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getAuthorProfileImageUrl()).endsWith("new.png");
    }

    /** 깨지면: 프로필 이미지 행이 둘인 협업자가 협업자 목록에 두 번 나온다. */
    @Test
    void 프로필_이미지가_두_장인_협업자도_목록에_한_번만_나온다() {
        jdbc.update("INSERT INTO member (email, nickname) VALUES (?, 'owner')", marker + "-o@test.local");
        long ownerId = jdbc.queryForObject("SELECT id FROM member WHERE email = ?", Long.class, marker + "-o@test.local");
        jdbc.update("INSERT INTO trip (member_id, title, start_date, end_date) VALUES (?, 'plist', '2026-07-01', '2026-07-02')", ownerId);
        long tripId = jdbc.queryForObject("SELECT MAX(id) FROM trip WHERE member_id = ?", Long.class, ownerId);
        tripService.inviteCollaborator(tripId, memberId, "EDITOR", ownerId);

        List<CollaboratorItem> collaborators = tripService.getCollaborators(tripId, ownerId);

        assertThat(collaborators).hasSize(1);
        assertThat(collaborators.get(0).profileImageUrl()).endsWith("new.png");
    }
}
