package edu.bsu.worgit.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.exception.AuthenticationException;
import edu.bsu.worgit.repository.UserRepository;
import edu.bsu.worgit.security.IssuedToken;
import edu.bsu.worgit.security.JwtService;
import edu.bsu.worgit.security.LoginRateLimiter;

/** Login only: there is no registration. Accounts are inserted into the database by the owner. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginRateLimiter limiter;
    /** Compared against when the id is unknown, so unknown ids and wrong passwords take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, LoginRateLimiter limiter) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.limiter = limiter;
        this.dummyHash = encoder.encode("worgit-timing-equalizer");
    }

    public LoginResult login(String rawId, String rawPassword, String clientIp) {
        String id = rawId == null ? "" : rawId.strip();
        String password = rawPassword == null ? "" : rawPassword;

        limiter.checkAllowed(id, clientIp);

        Optional<UserAccount> found = users.findById(id);
        String hash = found.map(UserAccount::passwordHash).orElse(dummyHash);
        boolean passwordMatches = encoder.matches(password, hash);

        if (found.isEmpty() || !passwordMatches) {
            limiter.recordFailure(id, clientIp);
            throw new AuthenticationException();
        }

        limiter.reset(id, clientIp);
        UserAccount user = found.get();
        IssuedToken issued = jwt.issue(user);
        return new LoginResult(issued.token(), issued.expiresAt(), user);
    }
}
