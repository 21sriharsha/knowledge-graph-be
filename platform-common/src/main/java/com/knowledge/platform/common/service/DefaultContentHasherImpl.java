package com.knowledge.platform.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * Default ContentHasher.
 *
 * <p>See {@link ContentHasher} for what this provides and why it exists.
 */
@Service
public class DefaultContentHasherImpl implements ContentHasher {

    private static final String ALGORITHM = "SHA-256";

    @Override
    public String hash(String content) {
        return HexFormat.of().formatHex(digest().digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " is required but unavailable", e);
        }
    }
}
