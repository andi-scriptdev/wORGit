package edu.bsu.worgit.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record Organization(
        UUID orgId,
        String name,
        String category,
        String description,
        String logoUrl,
        String coverPhotoUrl,
        String contactEmail,
        String contactPhone,
        boolean requiresApproval,
        BigDecimal membershipFee,
        List<String> interests,
        List<SocialLink> socialLinks,
        int memberCount) {}
