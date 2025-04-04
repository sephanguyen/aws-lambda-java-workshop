package com.dragonresort;

import java.util.UUID;

public record Dragon(
        String id,
        String name,
        String age,
        String color,
        String type
) {
    public Dragon {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }
}
