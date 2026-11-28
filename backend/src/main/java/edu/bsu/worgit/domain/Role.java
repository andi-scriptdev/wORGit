package edu.bsu.worgit.domain;

import java.util.Optional;

/** The four roles of the spec (section 3), derived from users.user_type and admins.admin_role. */
public enum Role {
    STUDENT,
    ORGANIZATION_FACILITATOR,
    FACULTY_ADVISER,
    SYSTEM_ADMINISTRATOR;

    public boolean isAdmin() {
        return this != STUDENT;
    }

    /** First screen after login: ST-01 for students, AD-01 (in the role's variant) for staff. */
    public String homeScreen() {
        return isAdmin() ? "AD-01" : "ST-01";
    }

    /** Maps admins.admin_role to a Role; anything else (including null) is not a valid admin role. */
    public static Optional<Role> fromAdminRole(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value) {
            case "ORGANIZATION_FACILITATOR" -> Optional.of(ORGANIZATION_FACILITATOR);
            case "FACULTY_ADVISER" -> Optional.of(FACULTY_ADVISER);
            case "SYSTEM_ADMINISTRATOR" -> Optional.of(SYSTEM_ADMINISTRATOR);
            default -> Optional.empty();
        };
    }
}
