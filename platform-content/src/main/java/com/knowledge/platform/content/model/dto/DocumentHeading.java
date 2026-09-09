package com.knowledge.platform.content.model.dto;

/**
 * A heading in the document outline, used to build a table of contents without walking the whole
 * block tree on every request.
 */
public record DocumentHeading(int level, String anchorId, String text) {
}
