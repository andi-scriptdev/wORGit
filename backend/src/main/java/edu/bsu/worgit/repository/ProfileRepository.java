package edu.bsu.worgit.repository;

import java.util.Optional;
import java.util.Set;

import edu.bsu.worgit.domain.PeopleRows;

/** My Profile (ST-07) and the admin profile (AD-12). Password hashes are BCrypt strings. */
public interface ProfileRepository {

    /** Empty when the id is not a student. */
    Optional<PeopleRows.StudentProfile> findStudentProfile(String studentId);

    /** Updates users.display_name and the students row (bio, visible_in_matching). */
    void updateStudentProfile(String studentId, String displayName, String bio, boolean visibleInMatching);

    /** Replaces every interest of the student. */
    void replaceStudentInterests(String studentId, Set<String> interests);

    /** For staff, whose only editable profile field is the display name. */
    void updateDisplayName(String userId, String displayName);

    void updatePasswordHash(String userId, String passwordHash);
}
