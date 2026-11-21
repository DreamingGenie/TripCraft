package com.tripcraft.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PostUpdateRequest {

    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")  // post.title VARCHAR(100)
    private String title;

    @NotBlank(message = "내용을 입력해주세요.")
    private String content;
}
