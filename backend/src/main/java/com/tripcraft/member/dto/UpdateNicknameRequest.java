package com.tripcraft.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateNicknameRequest {

    @NotBlank
    @Size(min = 2, max = 20)
    private String nickname;
}
