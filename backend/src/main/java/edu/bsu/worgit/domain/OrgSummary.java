package edu.bsu.worgit.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record OrgSummary(
        UUID orgId,
        String name,
        String category,
        String logoUrl,
        String coverPhotoUrl,
        boolean requiresApproval,
        BigDecimal membershipFee,
        int memberCount) {}
