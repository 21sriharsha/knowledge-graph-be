package com.knowledge.platform.author.model.entity;

/**
 * What an account may do.
 *
 * <p>A closed set, checked by a database constraint as well as by this type. Roles are granted by an
 * administrator and stored by this platform; they are never read from an access token, because the
 * identity provider's own role claim describes its database and its user metadata is editable by the
 * user it describes.
 */
public enum PlatformRole {

    /** May operate sources, trigger syncs and control publication state. */
    AUTHOR,

    /** May additionally manage accounts: grant roles and link accounts to authors. */
    ADMIN
}
