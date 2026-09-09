package com.knowledge.platform.source.integration.adapter.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * GitHub REST API response shapes.
 *
 * <p>These records exist only inside the GitHub adapter and are never returned from it. That is the
 * boundary rule the whole source module is built around: a change to GitHub's API shape is a change
 * to this file, not a change that ripples into ingestion or content.
 *
 * <p>Every record ignores unknown properties. GitHub adds fields continuously, and failing to
 * deserialize because a new field appeared would be a self-inflicted outage.
 */
final class GitHubApiModels {

    private GitHubApiModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tree(String sha, List<TreeEntry> tree, boolean truncated) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TreeEntry(String path, String type, String sha, Long size) {

        /** GitHub reports directories as "tree" and files as "blob". */
        boolean isFile() {
            return "blob".equals(type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileContent(String path, String sha, String content, String encoding, Long size) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Commit(String sha) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PushPayload(
            String ref,
            String after,
            @JsonProperty("head_commit") HeadCommit headCommit,
            List<PushCommit> commits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HeadCommit(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PushCommit(String id, List<String> added, List<String> modified, List<String> removed) {
    }
}
