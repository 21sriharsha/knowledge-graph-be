package com.knowledge.platform.source.integration.adapter.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** GitLab REST API response shapes. Confined to the GitLab adapter. */
final class GitLabApiModels {

    private GitLabApiModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TreeEntry(String id, String name, String type, String path) {

        /** GitLab reports directories as "tree" and files as "blob". */
        boolean isFile() {
            return "blob".equals(type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileContent(
            @JsonProperty("file_path") String filePath,
            @JsonProperty("blob_id") String blobId,
            String content,
            String encoding,
            Long size,
            @JsonProperty("last_commit_id") String lastCommitId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Commit(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PushPayload(
            String ref,
            @JsonProperty("checkout_sha") String checkoutSha,
            List<PushCommit> commits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PushCommit(String id, List<String> added, List<String> modified, List<String> removed) {
    }
}
