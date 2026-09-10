package com.knowledge.platform.author.repository;

import com.knowledge.platform.author.model.entity.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** The lookup on every authenticated request: subject is unique only within an issuer. */
    Optional<Account> findByIssuerAndSubject(String issuer, String subject);

    Optional<Account> findByAuthorId(UUID authorId);

    List<Account> findAllByOrderByCreatedAtAsc();

    /**
     * Creates the account for an identity, or does nothing if it already exists.
     *
     * <p>A native upsert rather than "look, then insert". Two requests arriving together on a first
     * sign-in both saw no account and both inserted; one lost on the unique constraint, and because
     * that happened inside the authentication converter, Spring Security reported it as a rejected
     * token. The user was told their credentials were bad when they had just signed in successfully.
     *
     * <p>Concurrent first sign-ins are not exotic: a single page render that makes two backend calls
     * is enough, which is most pages. Letting the database resolve the race is the only version of
     * this that is actually safe -- a check-then-act in application code has a window no matter how
     * small it is made.
     *
     * @return the number of rows inserted: 1 for the request that created it, 0 for any that raced
     */
    @Modifying
    @Query(value = """
            insert into author.accounts
                (id, issuer, subject, email, display_name, created_at, updated_at)
            values (:id, :issuer, :subject, :email, :displayName, now(), now())
            on conflict (issuer, subject) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("issuer") String issuer,
            @Param("subject") String subject,
            @Param("email") String email,
            @Param("displayName") String displayName);
}
