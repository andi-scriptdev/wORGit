package edu.bsu.worgit.domain;

/** Values of certificates.certificate_type, fixed from the participant's role when the certificate is made. */
public enum CertificateType {
    PARTICIPATION, ORGANIZER;

    public static CertificateType forRole(EventRole role) {
        return role == EventRole.ORGANIZER ? ORGANIZER : PARTICIPATION;
    }
}
