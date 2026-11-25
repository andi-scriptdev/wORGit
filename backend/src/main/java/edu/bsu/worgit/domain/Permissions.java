package edu.bsu.worgit.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import edu.bsu.worgit.exception.ForbiddenException;

/** The role matrix of spec section 3, plus organization scoping through canManage. */
public final class Permissions {
    private Permissions() {}

    private static final Map<Role, Set<Capability>> MATRIX = new EnumMap<>(Role.class);

    static {
        Set<Capability> readOnly = EnumSet.of(
                Capability.VIEW_DASHBOARD, Capability.VIEW_ORGANIZATIONS, Capability.VIEW_MEMBERS,
                Capability.VIEW_STUDENT_SUMMARY, Capability.VIEW_APPLICATIONS, Capability.VIEW_PAYMENTS,
                Capability.EDIT_OWN_ADMIN_PROFILE);

        Set<Capability> adviser = EnumSet.copyOf(readOnly);
        adviser.add(Capability.OPEN_STUDENT_SUMMARY_PAGE);
        adviser.add(Capability.VIEW_NOTIFICATIONS);

        Set<Capability> facilitator = EnumSet.copyOf(readOnly);
        facilitator.add(Capability.EDIT_ORGANIZATION_PROFILE);
        facilitator.add(Capability.REVIEW_APPLICATIONS);
        facilitator.add(Capability.RECORD_PAYMENTS);
        facilitator.add(Capability.MANAGE_EVENTS);
        facilitator.add(Capability.MODERATE_CHAT);
        facilitator.add(Capability.VIEW_NOTIFICATIONS);

        Set<Capability> systemAdmin = EnumSet.allOf(Capability.class);
        systemAdmin.remove(Capability.VIEW_NOTIFICATIONS);   // System Administrator has no notifications tab

        MATRIX.put(Role.STUDENT, EnumSet.noneOf(Capability.class));
        MATRIX.put(Role.FACULTY_ADVISER, adviser);
        MATRIX.put(Role.ORGANIZATION_FACILITATOR, facilitator);
        MATRIX.put(Role.SYSTEM_ADMINISTRATOR, systemAdmin);
    }

    public static boolean has(Role role, Capability capability) {
        return MATRIX.get(role).contains(capability);
    }

    /** For actions that are not tied to one organization (e.g. creating an organization). */
    public static void requireGlobal(AuthenticatedUser user, Capability capability) {
        if (!has(user.role(), capability)) {
            throw new ForbiddenException();
        }
    }

    /** For actions on one organization: the role needs the capability AND must manage that organization. */
    public static void requireForOrg(AuthenticatedUser user, Capability capability, UUID orgId) {
        if (!has(user.role(), capability) || !user.canManage(orgId)) {
            throw new ForbiddenException();
        }
    }
}
