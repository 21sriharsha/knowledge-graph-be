package com.knowledge.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The composition root.
 *
 * <p>One deployable, assembled from ten Maven modules. The modules exist so that a boundary violation
 * is a compile error rather than a code-review opinion; this class is the single place they are
 * brought together, and the only artifact that produces a runnable jar.
 *
 * <p>Scanning is rooted at {@code com.knowledge.platform}, which every module shares, so components,
 * entities, repositories and configuration properties are discovered wherever they live.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.knowledge.platform")
public class KnowledgePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgePlatformApplication.class, args);
    }
}
