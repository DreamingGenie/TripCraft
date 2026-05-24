package com.tripcraft.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PostListPageResponse {

    private List<PostListItem> items;
    private int total;
    private int page;
    private int size;
}
