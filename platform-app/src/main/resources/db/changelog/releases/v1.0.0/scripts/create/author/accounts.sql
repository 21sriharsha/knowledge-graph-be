-- Sign-in identity, kept deliberately separate from author identity.
--
-- These are two different things and conflating them would be a security bug. An `author` is a
-- byline: created implicitly during ingestion from a name in frontmatter or a repository's owner,
-- never verified, and existing for people who have never visited the site. An `account` is a person
-- who proved control of an identity at an OIDC provider.
--
-- The link between them is therefore explicit and administrative. Matching them automatically on
-- email would mean an unverified string written in a Markdown file could claim an identity, and Git
-- commit emails routinely differ from sign-in emails anyway.
CREATE TABLE author.accounts (
    id           UUID         NOT NULL,

    -- The OIDC `sub`, scoped by `iss`. Subject is unique only within an issuer, so the pair is the
    -- real key -- storing the issuer is what stops a second identity provider, added later, from
    -- colliding with an existing account.
    issuer       VARCHAR(500) NOT NULL,
    subject      VARCHAR(255) NOT NULL,

    -- Copied from the token for display and support, never for authorization. Whether an address is
    -- verified is the provider's business, and the moment this column decided permissions it would
    -- become a way to grant them by changing an email.
    email        VARCHAR(320),
    display_name VARCHAR(200),

    -- Null until an administrator links this account to a byline. Null means "can sign in, owns no
    -- articles", which is the correct state for a new account and for anyone who is not an author.
    author_id    UUID,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ,

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_identity UNIQUE (issuer, subject),
    CONSTRAINT fk_accounts_author
        FOREIGN KEY (author_id) REFERENCES author.authors (id) ON DELETE SET NULL
);

-- One byline, one account. Two accounts claiming the same author would make "who wrote this" and
-- "who may edit this" disagree.
CREATE UNIQUE INDEX uq_accounts_author ON author.accounts (author_id) WHERE author_id IS NOT NULL;

-- Every request resolves an account by the token's subject, so this is the hot lookup.
CREATE INDEX idx_accounts_email ON author.accounts (email);

-- Authorization lives here and nowhere else.
--
-- Not in the token: the identity provider's own `role` claim is "authenticated" for every signed-in
-- user, and provider-side user metadata is editable by the user it describes. A permission model
-- built on either would let a person grant themselves whatever they liked. The token answers who
-- you are; this table answers what you may do.
CREATE TABLE author.account_roles (
    account_id UUID        NOT NULL,
    role       VARCHAR(32) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_account_roles PRIMARY KEY (account_id, role),
    CONSTRAINT fk_account_roles_account
        FOREIGN KEY (account_id) REFERENCES author.accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_account_roles_role CHECK (role IN ('AUTHOR', 'ADMIN'))
);
