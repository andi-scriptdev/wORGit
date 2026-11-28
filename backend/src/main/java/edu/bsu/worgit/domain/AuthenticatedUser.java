package edu.bsu.worgit.domain;

import java.util.Set;
import java.util.UUID;

/** The principal of an authenticated request. Loaded from the database on every request. */
public record AuthenticatedUser(String userId, String displayName, Role role, Set<UUID> managedOrgIds) {

    public AuthenticatedUser {
        managedOrgIds = managedOrgIds == null ? Set.of() : Set.copyOf(managedOrgIds);
    }

    public static AuthenticatedUser from(UserAccount account) {
        return new AuthenticatedUser(account.userId(), account.displayName(), account.role(), account.managedOrgIds());
    }

    /** Admin.canManage(orgId): System Administrators manage everything, others only their rows. */
    public boolean canManage(UUID orgId) {
        return role == Role.SYSTEM_ADMINISTRATOR || managedOrgIds.contains(orgId);
    }
}
