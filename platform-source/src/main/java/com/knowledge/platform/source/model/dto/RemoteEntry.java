package com.knowledge.platform.source.model.dto;

/**
 * One file in a provider's repository tree, normalized.
 *
 * @param path repository-relative path
 * @param blobId the provider's identifier for this exact content, where it offers one. Used to skip
 *     files that cannot have changed, without downloading them
 * @param sizeBytes reported size, or -1 when the provider does not say
 */
public record RemoteEntry(String path, String blobId, long sizeBytes) {

    private static final String[] MARKDOWN_EXTENSIONS = {".md", ".markdown", ".mdx"};

    public boolean isMarkdown() {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        for (String extension : MARKDOWN_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
