package de.samply.manager.types;

import java.util.UUID;

public record Block(UUID id, BlockKey key, String content ) {

}
