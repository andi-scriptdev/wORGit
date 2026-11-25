package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.exception.AuthenticationException;
import edu.bsu.worgit.exception.TooManyAttemptsException;
import edu.bsu.worgit.security.JwtService;
import edu.bsu.worgit.security.LoginRateLimiter;
import edu.bsu.worgit.support.FakeUserRepository;
import edu.bsu.worgit.support.TestClock;
import edu.bsu.worgit.support.TestData;

class AuthServiceTest {

    private static final String IP = "10.0.0.1";

    private FakeUserRepository users;
    private JwtService jwt;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        TestClock clock = new TestClock();
        users = new FakeUserRepository()
                .add(TestData.account("23-10457", Role.STUDENT, "correct-horse"))
                .add(TestData.account("ADM-001", Role.ORGANIZATION_FACILITATOR, "correct-horse", UUID.randomUUID()))
                .add(TestData.account("ADM-002", Role.SYSTEM_ADMINISTRATOR, "correct-horse"))
                .add(TestData.account("ADM-003", Role.FACULTY_ADVISER, "correct-horse", UUID.randomUUID()));
        jwt = new JwtService(TestData.props(), clock);
        auth = new AuthService(users, TestData.ENCODER, jwt, new LoginRateLimiter(TestData.props(), clock));
    }

    @Test
    void studentLogsInAndLandsOnTheStudentDashboard() {
        LoginResult result = auth.login("23-10457", "correct-horse", IP);
        assertThat(result.user().role()).isEqualTo(Role.STUDENT);
        assertThat(result.user().role().homeScreen()).isEqualTo("ST-01");
        assertThat(jwt.parseUserId(result.token())).contains("23-10457");
    }

    @Test
    void everyStaffRoleLandsOnTheAdminDashboard() {
        for (String id : new String[] { "ADM-001", "ADM-002", "ADM-003" }) {
            LoginResult result = auth.login(id, "correct-horse", IP);
            assertThat(result.user().role().homeScreen()).as(id).isEqualTo("AD-01");
        }
        assertThat(auth.login("ADM-003", "correct-horse", IP).user().role()).isEqualTo(Role.FACULTY_ADVISER);
    }

    @Test
    void idIsTrimmed() {
        assertThat(auth.login("  23-10457 ", "correct-horse", IP).user().userId()).isEqualTo("23-10457");
    }

    @Test
    void wrongPasswordAndUnknownIdGiveTheSameError() {
        assertThatThrownBy(() -> auth.login("23-10457", "wrong-password", IP))
                .isInstanceOf(AuthenticationException.class).hasMessage("ID or password is incorrect");
        assertThatThrownBy(() -> auth.login("99-99999", "correct-horse", IP))
                .isInstanceOf(AuthenticationException.class).hasMessage("ID or password is incorrect");
        assertThatThrownBy(() -> auth.login(null, null, IP)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void aPlainTextPasswordInTheDatabaseNeverLogsAnyoneIn() {
        users.add(new UserAccount("23-00001", "Plain Text", "Plain", "23-00001@g.batstate-u.edu.ph",
                "secret123", Role.STUDENT, null));
        assertThatThrownBy(() -> auth.login("23-00001", "secret123", IP)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void fiveFailuresLockTheLoginEvenForTheRightPassword() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> auth.login("23-10457", "wrong-password", IP))
                    .isInstanceOf(AuthenticationException.class);
        }
        assertThatThrownBy(() -> auth.login("23-10457", "correct-horse", IP))
                .isInstanceOf(TooManyAttemptsException.class);
        // another client is not locked out
        assertThat(auth.login("23-10457", "correct-horse", "10.0.0.2").user().userId()).isEqualTo("23-10457");
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCount() {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> auth.login("23-10457", "wrong-password", IP))
                    .isInstanceOf(AuthenticationException.class);
        }
        auth.login("23-10457", "correct-horse", IP);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> auth.login("23-10457", "wrong-password", IP))
                    .isInstanceOf(AuthenticationException.class);
        }
        assertThat(auth.login("23-10457", "correct-horse", IP)).isNotNull();
    }
}
