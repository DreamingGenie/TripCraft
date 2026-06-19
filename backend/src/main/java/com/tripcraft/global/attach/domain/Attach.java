package com.tripcraft.global.attach.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Attach {
    private Long id;
    private String name;
    private String hostName;
    private long size;
    private String mimetype;
    private String hostPath;
    private String target;
    private long targetId;
    private LocalDateTime createdAt;
}
