package edu.bsu.worgit.domain;

import java.util.Set;
import java.util.UUID;

/** A row of users (+ admins + admin_managed_orgs). Holds the password hash: never send it out. */
public record UserAccount(
        String userId,
        String fullName,
        String displayName,
        String email,
        String passwordHash,
        Role role,
        Set<UUID> managedOrgIds) {

    public UserAccount {
        managedOrgIds = managedOrgIds == null ? Set.of() : Set.copyOf(managedOrgIds);
    }
}
