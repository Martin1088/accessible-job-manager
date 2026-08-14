package de.samply.manager.types;

import java.util.List;
import java.util.UUID;

public record Block(UUID id, BlockKey key, String content, List<String> items) {

    public Block {
        content = content == null ? "" : content;
        items = items == null ? List.of() : items;
    }
}
