-- A connected external repository (GitHub, GitLab, Azure DevOps).
--
-- Provider credentials are stored as ciphertext produced by the source module's credential cipher.
-- A plaintext token must never reach this table: it would end up in backups, in replicas, and in
-- every developer's local dump.
CREATE TABLE source.repositories (
    id                        UUID         NOT NULL,
    source_type               VARCHAR(32)  NOT NULL,
    display_name              VARCHAR(200) NOT NULL,
    owner                     VARCHAR(200) NOT NULL,
    repository                VARCHAR(200) NOT NULL,
    project                   VARCHAR(200),
    default_branch            VARCHAR(200) NOT NULL DEFAULT 'main',
    content_path              VARCHAR(500) NOT NULL DEFAULT '',
    api_base_url              VARCHAR(500),
    access_token_ciphertext   TEXT,
    webhook_secret_ciphertext TEXT,
    owner_author_id           UUID,
    active                    BOOLEAN      NOT NULL DEFAULT TRUE,
    last_synced_revision      VARCHAR(200),
    last_synced_at            TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_repositories PRIMARY KEY (id),
    CONSTRAINT fk_repositories_owner_author
        FOREIGN KEY (owner_author_id) REFERENCES author.authors (id) ON DELETE SET NULL,
    CONSTRAINT ck_repositories_source_type
        CHECK (source_type IN ('GITHUB', 'GITLAB', 'AZURE_DEVOPS')),
    -- Azure DevOps addresses a repository as organisation/project/repository; GitHub and GitLab have
    -- no project dimension. The check keeps that provider difference explicit in the schema instead
    -- of leaving it to adapter code.
    CONSTRAINT ck_repositories_project_required
        CHECK (source_type <> 'AZURE_DEVOPS' OR project IS NOT NULL)
);

-- One connected repository per provider coordinate. COALESCE keeps the uniqueness meaningful for the
-- providers where project is NULL, which the default NULLS DISTINCT behaviour would not.
CREATE UNIQUE INDEX uq_repositories_coordinate
    ON source.repositories (source_type, owner, repository, COALESCE(project, ''));
