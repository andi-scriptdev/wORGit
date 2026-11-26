package edu.bsu.worgit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import edu.bsu.worgit.domain.ApplicationStatus;
import edu.bsu.worgit.domain.FeeStatus;
import edu.bsu.worgit.domain.Rows;
import edu.bsu.worgit.domain.SocialLink;

/** What the app receives (these records are sent as JSON; they replace the old toHtml()). */
public final class Views {
    private Views() {}

    /** The card state on ST-02 / ST-03: Joined, Approval required + Apply, Join, or a pending application. */
    public enum CardState { JOINED, APPLY, JOIN, PENDING }

    public record OrgCard(UUID orgId, String name, String category, String logoUrl, String coverPhotoUrl,
                          int memberCount, boolean requiresApproval, CardState state) {}

    public record MyApplication(UUID applicationId, UUID orgId, String organizationName,
                                LocalDate dateApplied, ApplicationStatus status) {}

    /** feeStatus and roomId are only set for members. */
    public record Membership(CardState state, FeeStatus feeStatus, UUID roomId) {}

    public record OrgDetail(UUID orgId, String name, String category, String logoUrl, String coverPhotoUrl,
                            int memberCount, List<String> interests, String description, String contactEmail,
                            String contactPhone, List<SocialLink> socialLinks, boolean requiresApproval,
                            List<Rows.Event> upcomingEvents, Membership membership) {}

    public record StaffOrgRow(UUID orgId, String name, String category, int memberCount, boolean requiresApproval,
                              BigDecimal membershipFee, boolean canEditProfile, boolean canEditAllFields,
                              boolean canRemove) {}

    public record MemberView(String studentId, String fullName, LocalDate joinedOn, FeeStatus feeStatus) {}

    public record ApplicationList(int pending, int approved, int rejected, List<Rows.Application> items) {}

    public record CreatedOrg(UUID orgId) {}
}
