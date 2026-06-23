package com.tripcraft.plan.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TripCollaborator {
    private Long          id;
    private Long          tripId;
    private Long          memberId;
    private String        role;       // EDITOR | VIEWER
    private LocalDateTime invitedAt;
}
