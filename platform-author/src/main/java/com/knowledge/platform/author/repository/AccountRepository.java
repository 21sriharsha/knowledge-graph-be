package com.knowledge.platform.author.repository;

import com.knowledge.platform.author.model.entity.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** The lookup on every authenticated request: subject is unique only within an issuer. */
    Optional<Account> findByIssuerAndSubject(String issuer, String subject);

    Optional<Account> findByAuthorId(UUID authorId);

    List<Account> findAllByOrderByCreatedAtAsc();
}
