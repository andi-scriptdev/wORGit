package edu.bsu.worgit.web;

import java.time.Instant;

import edu.bsu.worgit.service.LoginResult;

/** What the app needs after login: the token, and where to go (home = ST-01 or AD-01 in the role's variant). */
public record LoginResponse(String token, String tokenType, Instant expiresAt, UserSummary user) {

    public record UserSummary(String userId, String displayName, String role, String home) {}

    public static LoginResponse from(LoginResult result) {
        var u = result.user();
        return new LoginResponse(result.token(), "Bearer", result.expiresAt(),
                new UserSummary(u.userId(), u.displayName(), u.role().name(), u.role().homeScreen()));
    }
}
