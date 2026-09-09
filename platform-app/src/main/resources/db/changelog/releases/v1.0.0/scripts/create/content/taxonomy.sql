-- Topics are the coarse subject areas a reader browses by; tags are the fine-grained labels an
-- author writes in Markdown frontmatter. Both are canonical: they come from the content itself.
CREATE TABLE content.topics (
    id          UUID         NOT NULL,
    slug        VARCHAR(160) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_topics PRIMARY KEY (id),
    CONSTRAINT uq_topics_slug UNIQUE (slug)
);

CREATE TABLE content.tags (
    id         UUID         NOT NULL,
    slug       VARCHAR(160) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_tags PRIMARY KEY (id),
    CONSTRAINT uq_tags_slug UNIQUE (slug)
);
