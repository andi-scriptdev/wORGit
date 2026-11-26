package edu.bsu.worgit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.support.TestClock;
import edu.bsu.worgit.support.TestData;

class JwtServiceTest {

    private TestClock clock;
    private JwtService jwt;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        jwt = new JwtService(TestData.props(), clock);
    }

    private UserAccount student(String id) {
        return TestData.account(id, Role.STUDENT, "secret123");
    }

    @Test
    void issuedTokenParsesBackToTheUserId() {
        IssuedToken issued = jwt.issue(student("23-10457"));
        assertThat(jwt.parseUserId(issued.token())).contains("23-10457");
    }

    @Test
    void tokenCarriesTheRoleAndExpiresAfterEightHours() {
        IssuedToken issued = jwt.issue(TestData.account("ADM-001", Role.FACULTY_ADVISER, "secret123"));
        assertThat(issued.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(8)));
        String payload = new String(Base64.getUrlDecoder().decode(issued.token().split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(payload).contains("\"role\":\"FACULTY_ADVISER\"").contains("\"sub\":\"ADM-001\"");
    }

    @Test
    void expiredTokenIsRejected() {
        IssuedToken issued = jwt.issue(student("23-10457"));
        clock.advance(Duration.ofHours(8).plusSeconds(1));
        assertThat(jwt.parseUserId(issued.token())).isEmpty();
    }

    @Test
    void tokenIsStillValidJustBeforeExpiry() {
        IssuedToken issued = jwt.issue(student("23-10457"));
        clock.advance(Duration.ofHours(7).plusMinutes(59));
        assertThat(jwt.parseUserId(issued.token())).contains("23-10457");
    }

    @Test
    void tamperedTokenIsRejected() {
        String a = jwt.issue(student("23-10457")).token();
        String b = jwt.issue(student("23-99999")).token();
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        String forged = pa[0] + "." + pb[1] + "." + pa[2];   // another user's payload, the first token's signature
        assertThat(jwt.parseUserId(forged)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService(TestData.props("another-secret-another-secret-another-1234"), clock);
        assertThat(jwt.parseUserId(other.issue(student("23-10457")).token())).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwt.parseUserId("not-a-token")).isEmpty();
        assertThat(jwt.parseUserId("")).isEmpty();
        assertThat(jwt.parseUserId(null)).isEmpty();
    }

    @Test
    void shortSecretStopsTheServerFromStarting() {
        assertThatThrownBy(() -> new JwtService(TestData.props("too-short"), clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
