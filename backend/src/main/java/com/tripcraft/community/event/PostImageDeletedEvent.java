package com.tripcraft.community.event;

import java.util.List;

public record PostImageDeletedEvent(List<String> hostPaths) {}
