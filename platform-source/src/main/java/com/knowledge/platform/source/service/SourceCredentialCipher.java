package com.knowledge.platform.source.service;



/**
 * Encrypts and decrypts provider credentials.
 *
 * <p>Provider access tokens and webhook secrets must never be persisted in plaintext: the database
 * ends up in backups, in replicas and in developers' local dumps, and a token there is a write
 * credential to somebody's source repository.
 *
 * <p>AES-256-GCM with a random 96-bit IV per encryption. GCM rather than CBC because it authenticates
 * as well as encrypts -- tampering with stored ciphertext produces a decryption failure rather than
 * plausible garbage. The IV is generated fresh for every call and prefixed to the ciphertext; reusing
 * one across encryptions under the same key is the single fatal mistake with GCM.
 *
 * <p>The key comes from configuration and therefore from the environment. It is never derived from a
 * password and never defaulted -- an application configured without one refuses to start rather than
 * silently encrypting with a value an attacker could guess.
 */
public interface SourceCredentialCipher {

    /** @return base64 of IV followed by ciphertext, or null when there is nothing to encrypt */
    String encrypt(String plaintext);

    String decrypt(String encoded);
}
