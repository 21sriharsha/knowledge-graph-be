package com.knowledge.platform.author.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who is an administrator before anyone has been made one.
 *
 * <p>Roles are granted by administrators, and a new installation has none. Naming the first one in
 * configuration keeps that decision with whoever controls the deployment rather than with whoever
 * happens to sign in first.
 *
 * <p>This trusts the identity provider to have verified the address, which is why the property
 * should be emptied once a real administrator exists: while it is set, anyone able to obtain a token
 * for one of these addresses becomes an administrator.
 *
 * @param bootstrapAdminEmails email addresses granted ADMIN on sign-in
 */
@ConfigurationProperties(prefix = "knowledge.accounts")
public record AccountSecurityProperties(List<String> bootstrapAdminEmails) {

    public AccountSecurityProperties {
        bootstrapAdminEmails =
                bootstrapAdminEmails == null ? List.of() : List.copyOf(bootstrapAdminEmails);
    }

    /** Lower-cased and trimmed, so a configuration typo in casing is not a silent no-op. */
    public Set<String> normalisedBootstrapAdminEmails() {
        return bootstrapAdminEmails.stream()
                .filter(email -> email != null && !email.isBlank())
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
