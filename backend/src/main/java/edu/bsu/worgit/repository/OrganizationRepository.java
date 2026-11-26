package edu.bsu.worgit.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import edu.bsu.worgit.domain.OrgInput;
import edu.bsu.worgit.domain.OrgSummary;
import edu.bsu.worgit.domain.Organization;
import edu.bsu.worgit.domain.Rows;

public interface OrganizationRepository {

    /** Organizations ordered by name. text/category null = no filter; onlyOrgIds null = all, empty = none. */
    List<OrgSummary> searchOrganizations(String text, String category, Set<UUID> onlyOrgIds);

    Optional<Organization> findOrganization(UUID orgId);

    /** Inserts the organization, its interests and social links. The chat room is made by a database trigger. */
    UUID insertOrganization(OrgInput input);

    /** Replaces every editable field, the interests and the social links. Logo and cover are untouched. */
    void updateOrganization(UUID orgId, OrgInput input);

    /** Case-insensitive; exceptOrgId (nullable) is left out of the check. */
    boolean organizationNameTaken(String name, UUID exceptOrgId);

    /** The five values of interest_options (also the allowed categories). */
    Set<String> allowedInterests();

    Optional<UUID> organizationRoomId(UUID orgId);

    Rows.RemovalImpact removalImpact(UUID orgId);

    void deleteOrganization(UUID orgId);
}
