package com.knowledge.platform.source.model.dto;

/**
 * A content file, normalized away from whatever the provider returned.
 *
 * <p>This is the contract between the source module and ingestion, and the reason ingestion has no
 * idea which provider it is processing. GitHub base64, GitLab's own base64 and Azure DevOps' raw
 * text all arrive here as decoded UTF-8.
 *
 * @param path repository-relative path, the article's stable identity within its repository
 * @param content decoded UTF-8 Markdown
 * @param revision the provider revision this content was read at
 * @param blobId provider content identifier, where available
 */
public record SourceFile(String path, String content, String revision, String blobId) {
}
