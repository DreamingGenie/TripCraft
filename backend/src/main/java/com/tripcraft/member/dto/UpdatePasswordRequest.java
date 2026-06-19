package com.tripcraft.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdatePasswordRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8)
    private String newPassword;
}
