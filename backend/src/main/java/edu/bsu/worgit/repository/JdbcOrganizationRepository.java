package edu.bsu.worgit.repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.OrgInput;
import edu.bsu.worgit.domain.OrgSummary;
import edu.bsu.worgit.domain.Organization;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.domain.SocialLink;

/**
 * organizations.interest_name is the organization's category (one of the five interests); the Java and JSON
 * name stays "category", as in the spec.
 */
@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {

    private final JdbcClient jdbc;

    public JdbcOrganizationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OrgSummary> searchOrganizations(String text, String category, Set<UUID> onlyOrgIds) {
        if (onlyOrgIds != null && onlyOrgIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT o.org_id, o.name, o.interest_name AS category, o.logo_url, o.cover_photo_url, o.requires_approval,
                       o.membership_fee,
                       (SELECT count(*) FROM org_members m WHERE m.org_id = o.org_id) AS member_count
                  FROM organizations o
                 WHERE 1 = 1
                """);
        Map<String, Object> params = new HashMap<>();
        if (onlyOrgIds != null) {
            sql.append(" AND o.org_id IN (:ids)");
            params.put("ids", onlyOrgIds);
        }
        if (text != null) {
            sql.append(" AND lower(o.name) LIKE :text");
            params.put("text", SqlUtil.likePattern(text));
        }
        if (category != null) {
            sql.append(" AND o.interest_name = :category");
            params.put("category", category);
        }
        sql.append(" ORDER BY o.name");
        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, n) -> new OrgSummary(
                        rs.getObject("org_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("logo_url"),
                        rs.getString("cover_photo_url"),
                        rs.getBoolean("requires_approval"),
                        rs.getBigDecimal("membership_fee"),
                        rs.getInt("member_count")))
                .list();
    }

    @Override
    public Optional<Organization> findOrganization(UUID orgId) {
        Optional<Organization> base = jdbc.sql("""
                SELECT o.org_id, o.name, o.interest_name AS category, o.description, o.logo_url, o.cover_photo_url,
                       o.contact_email, o.contact_phone, o.requires_approval, o.membership_fee,
                       (SELECT count(*) FROM org_members m WHERE m.org_id = o.org_id) AS member_count
                  FROM organizations o
                 WHERE o.org_id = :id
                """)
                .param("id", orgId)
                .query((rs, n) -> new Organization(
                        rs.getObject("org_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("description"),
                        rs.getString("logo_url"),
                        rs.getString("cover_photo_url"),
                        rs.getString("contact_email"),
                        rs.getString("contact_phone"),
                        rs.getBoolean("requires_approval"),
                        rs.getBigDecimal("membership_fee"),
                        List.of(),
                        List.of(),
                        rs.getInt("member_count")))
                .optional();
        if (base.isEmpty()) {
            return Optional.empty();
        }
        Organization o = base.get();
        List<String> interests = jdbc.sql("SELECT interest FROM org_interests WHERE org_id = :id ORDER BY interest")
                .param("id", orgId)
                .query((rs, n) -> rs.getString("interest"))
                .list();
        List<SocialLink> links = jdbc.sql("SELECT platform, url FROM org_social_links WHERE org_id = :id ORDER BY platform, url")
                .param("id", orgId)
                .query((rs, n) -> new SocialLink(rs.getString("platform"), rs.getString("url")))
                .list();
        return Optional.of(new Organization(o.orgId(), o.name(), o.category(), o.description(), o.logoUrl(),
                o.coverPhotoUrl(), o.contactEmail(), o.contactPhone(), o.requiresApproval(), o.membershipFee(),
                interests, links, o.memberCount()));
    }

    @Override
    public UUID insertOrganization(OrgInput in) {
        UUID id = jdbc.sql("""
                INSERT INTO organizations (name, interest_name, description, contact_email, contact_phone,
                                           requires_approval, membership_fee)
                VALUES (:name, :category, :description, :email, :phone, :approval, :fee)
                RETURNING org_id
                """)
                .param("name", in.name())
                .param("category", in.category())
                .param("description", in.description())
                .param("email", in.contactEmail())
                .param("phone", in.contactPhone())
                .param("approval", in.requiresApproval())
                .param("fee", in.membershipFee())
                .query((rs, n) -> rs.getObject("org_id", UUID.class))
                .single();
        replaceInterestsAndLinks(id, in);
        return id;
    }

    @Override
    public void updateOrganization(UUID orgId, OrgInput in) {
        jdbc.sql("""
                UPDATE organizations
                   SET name = :name, interest_name = :category, description = :description,
                       contact_email = :email, contact_phone = :phone,
                       requires_approval = :approval, membership_fee = :fee
                 WHERE org_id = :id
                """)
                .param("id", orgId)
                .param("name", in.name())
                .param("category", in.category())
                .param("description", in.description())
                .param("email", in.contactEmail())
                .param("phone", in.contactPhone())
                .param("approval", in.requiresApproval())
                .param("fee", in.membershipFee())
                .update();
        replaceInterestsAndLinks(orgId, in);
    }

    private void replaceInterestsAndLinks(UUID orgId, OrgInput in) {
        jdbc.sql("DELETE FROM org_interests WHERE org_id = :id").param("id", orgId).update();
        for (String interest : in.interests()) {
            jdbc.sql("INSERT INTO org_interests (org_id, interest) VALUES (:id, :interest)")
                    .param("id", orgId).param("interest", interest).update();
        }
        jdbc.sql("DELETE FROM org_social_links WHERE org_id = :id").param("id", orgId).update();
        for (SocialLink link : in.socialLinks()) {
            jdbc.sql("INSERT INTO org_social_links (org_id, platform, url) VALUES (:id, :platform, :url)")
                    .param("id", orgId).param("platform", link.platform()).param("url", link.url()).update();
        }
    }

    @Override
    public boolean organizationNameTaken(String name, UUID exceptOrgId) {
        Integer count;
        if (exceptOrgId == null) {
            count = jdbc.sql("SELECT count(*) FROM organizations WHERE lower(name) = lower(:name)")
                    .param("name", name)
                    .query(Integer.class).single();
        } else {
            count = jdbc.sql("SELECT count(*) FROM organizations WHERE lower(name) = lower(:name) AND org_id <> :except")
                    .param("name", name)
                    .param("except", exceptOrgId)
                    .query(Integer.class).single();
        }
        return count != null && count > 0;
    }

    @Override
    public Set<String> allowedInterests() {
        return new HashSet<>(jdbc.sql("SELECT name FROM interest_options")
                .query((rs, n) -> rs.getString("name"))
                .list());
    }

    @Override
    public Optional<UUID> organizationRoomId(UUID orgId) {
        return jdbc.sql("SELECT room_id FROM chat_rooms WHERE room_type = 'ORGANIZATION' AND org_id = :id")
                .param("id", orgId)
                .query((rs, n) -> rs.getObject("room_id", UUID.class))
                .optional();
    }

    @Override
    public Rows.RemovalImpact removalImpact(UUID orgId) {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM org_members WHERE org_id = :id) AS members,
                       (SELECT count(*) FROM events WHERE org_id = :id) AS events,
                       (SELECT count(*) FROM certificates c JOIN events e ON e.event_id = c.event_id
                         WHERE e.org_id = :id) AS certificates,
                       (SELECT count(*) FROM membership_applications WHERE org_id = :id) AS applications,
                       (SELECT count(*) FROM payment_records WHERE org_id = :id) AS payments
                """)
                .param("id", orgId)
                .query((rs, n) -> new Rows.RemovalImpact(
                        rs.getInt("members"), rs.getInt("events"), rs.getInt("certificates"),
                        rs.getInt("applications"), rs.getInt("payments")))
                .single();
    }

    @Override
    public void deleteOrganization(UUID orgId) {
        jdbc.sql("DELETE FROM organizations WHERE org_id = :id").param("id", orgId).update();
    }
}
