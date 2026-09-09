-- Article to tag/topic association. Canonical: both sides come from the author's frontmatter.
CREATE TABLE content.article_tags (
    article_id UUID NOT NULL,
    tag_id     UUID NOT NULL,

    CONSTRAINT pk_article_tags PRIMARY KEY (article_id, tag_id),
    CONSTRAINT fk_article_tags_article
        FOREIGN KEY (article_id) REFERENCES content.articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_tags_tag
        FOREIGN KEY (tag_id) REFERENCES content.tags (id) ON DELETE CASCADE
);

CREATE TABLE content.article_topics (
    article_id UUID NOT NULL,
    topic_id   UUID NOT NULL,

    CONSTRAINT pk_article_topics PRIMARY KEY (article_id, topic_id),
    CONSTRAINT fk_article_topics_article
        FOREIGN KEY (article_id) REFERENCES content.articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_topics_topic
        FOREIGN KEY (topic_id) REFERENCES content.topics (id) ON DELETE CASCADE
);
