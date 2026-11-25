package edu.bsu.worgit.domain;

/** What a staff role may do (spec section 3). Students hold none of these. */
public enum Capability {
    VIEW_DASHBOARD,
    VIEW_ORGANIZATIONS,
    VIEW_MEMBERS,
    VIEW_STUDENT_SUMMARY,          // read-only summary of a student (scoped to managed orgs)
    OPEN_STUDENT_SUMMARY_PAGE,     // the AD-05 page itself (System Admin, Adviser)
    VIEW_APPLICATIONS,
    VIEW_PAYMENTS,
    VIEW_NOTIFICATIONS,            // not for System Administrator
    EDIT_OWN_ADMIN_PROFILE,
    CREATE_ORGANIZATION,
    REMOVE_ORGANIZATION,
    EDIT_ORGANIZATION_ALL_FIELDS,  // name, category, fee, approval flag and profile content
    EDIT_ORGANIZATION_PROFILE,     // logo, cover, description, interests, contact, social links
    REMOVE_MEMBER,                 // System Administrator only until the spec says otherwise
    REVIEW_APPLICATIONS,
    RECORD_PAYMENTS,
    MANAGE_EVENTS,                 // events, attendance, certificates
    MODERATE_CHAT
}
