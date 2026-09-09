-- Author identity. The author module owns who someone is and how they are presented; it deliberately
-- holds no reference to articles, so the association is modelled only from the content side.
CREATE TABLE author.authors (
    id           UUID         NOT NULL,
    slug         VARCHAR(160) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    email        VARCHAR(320),
    biography    TEXT,
    avatar_url   VARCHAR(1000),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_authors PRIMARY KEY (id),
    CONSTRAINT uq_authors_slug UNIQUE (slug),
    CONSTRAINT uq_authors_email UNIQUE (email)
);

-- The DEFAULT covers INSERT only. updated_at is maintained by the JPA entity's @PreUpdate callback,
-- which is the single writer for these tables; no database trigger is used.
