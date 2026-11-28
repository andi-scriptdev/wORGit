package edu.bsu.worgit.support;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.security.AppProperties;

public final class TestData {
    private TestData() {}

    public static final String SECRET = "test-secret-test-secret-test-secret-123456";

    /** Cheap BCrypt (strength 4) so tests stay fast. */
    public static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    public static AppProperties props() {
        return props(SECRET);
    }

    public static AppProperties props(String secret) {
        return new AppProperties(
                new AppProperties.Jwt(secret, Duration.ofHours(8), "worgit"),
                new AppProperties.Login(5, Duration.ofMinutes(15)),
                new AppProperties.Cors(List.of("http://localhost:8081")));
    }

    public static UserAccount account(String id, Role role, String rawPassword, UUID... managedOrgs) {
        return new UserAccount(id, "Full " + id, "Name " + id, id.toLowerCase() + "@batstate-u.edu.ph",
                ENCODER.encode(rawPassword), role, Set.of(managedOrgs));
    }
}
