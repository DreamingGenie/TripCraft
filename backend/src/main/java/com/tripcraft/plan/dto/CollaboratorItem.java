package com.tripcraft.plan.dto;

public record CollaboratorItem(
        Long   memberId,
        String nickname,
        String role        // EDITOR | VIEWER
) {}
