package com.tripcraft.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostListItem {

    private Long id;
    private String title;
    private String authorNickname;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createdAt;
    private boolean liked;
    private boolean deleted; // 북마크·좋아요 목록에서 삭제된 글 표시용
    private String authorProfileImageUrl;
    private String coverImageUrl;
}
