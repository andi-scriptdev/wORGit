package edu.bsu.worgit.domain;

import java.math.BigDecimal;
import java.util.List;

/** The editable fields of an organization (logo and cover photo are uploaded separately). */
public record OrgInput(
        String name,
        String category,
        String description,
        boolean requiresApproval,
        BigDecimal membershipFee,
        String contactEmail,
        String contactPhone,
        List<String> interests,
        List<SocialLink> socialLinks) {}
