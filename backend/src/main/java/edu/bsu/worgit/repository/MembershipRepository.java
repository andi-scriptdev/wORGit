package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import edu.bsu.worgit.domain.Rows;

public interface MembershipRepository {

    boolean isMember(String studentId, UUID orgId);

    Set<UUID> memberOrgIds(String studentId);

    void addMember(UUID orgId, String studentId, LocalDate joinedOn);

    /** True when a membership was deleted. */
    boolean removeMember(UUID orgId, String studentId);

    /** Members ordered by name; search matches name or student id, case-insensitive (null = all). */
    List<Rows.Member> listMembers(UUID orgId, String search);
}
