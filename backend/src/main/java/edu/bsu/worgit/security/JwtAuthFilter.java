package edu.bsu.worgit.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates the bearer token, then loads the user from the database so the role and managed
 * organizations are always current (a role changed in the database applies on the next request).
 * An invalid token simply leaves the request unauthenticated; the entry point answers 401.
 */
class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwt;
    private final UserRepository users;
    private final JsonErrorWriter errors;

    JwtAuthFilter(JwtService jwt, UserRepository users, JsonErrorWriter errors) {
        this.jwt = jwt;
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            Optional<String> userId = jwt.parseUserId(header.substring(7).trim());
            if (userId.isPresent()) {
                try {
                    Optional<UserAccount> account = users.findById(userId.get());
                    if (account.isPresent()) {
                        SecurityContextHolder.getContext().setAuthentication(toAuthentication(account.get()));
                    }
                } catch (DataAccessException e) {
                    log.error("Database unavailable while authenticating a request", e);
                    errors.write(response, 503, "DATA_STORE", Messages.DATA_STORE);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static UsernamePasswordAuthenticationToken toAuthentication(UserAccount account) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (account.role() == Role.STUDENT) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STUDENT"));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            authorities.add(new SimpleGrantedAuthority("ROLE_" + account.role().name()));
        }
        return UsernamePasswordAuthenticationToken.authenticated(AuthenticatedUser.from(account), null, authorities);
    }
}
