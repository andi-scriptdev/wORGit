package edu.bsu.worgit.repository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.UserAccount;

/** Reads worgit.users, worgit.admins and worgit.admin_managed_orgs (schema is set by ?currentSchema=worgit). */
@Repository
public class JdbcUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserRepository.class);

    private final JdbcClient jdbc;

    public JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Row(String userId, String userType, String fullName, String displayName,
                       String email, String passwordHash, String adminRole) {}

    @Override
    public Optional<UserAccount> findById(String userId) {
        Optional<Row> found = jdbc.sql("""
                SELECT u.user_id, u.user_type, u.full_name, u.display_name, u.email, u.password_hash, a.admin_role
                  FROM users u
                  LEFT JOIN admins a ON a.user_id = u.user_id
                 WHERE u.user_id = :id
                """)
                .param("id", userId)
                .query((rs, rowNum) -> new Row(
                        rs.getString("user_id"),
                        rs.getString("user_type"),
                        rs.getString("full_name"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("admin_role")))
                .optional();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Row row = found.get();

        Role role;
        if ("STUDENT".equals(row.userType())) {
            role = Role.STUDENT;
        } else {
            Optional<Role> adminRole = Role.fromAdminRole(row.adminRole());
            if (adminRole.isEmpty()) {
                log.warn("User {} is an ADMIN without a valid admins row; access refused", row.userId());
                return Optional.empty();
            }
            role = adminRole.get();
        }

        Set<UUID> managed = Set.of();
        if (role == Role.ORGANIZATION_FACILITATOR || role == Role.FACULTY_ADVISER) {
            managed = new HashSet<>(jdbc.sql("SELECT org_id FROM admin_managed_orgs WHERE admin_id = :id")
                    .param("id", userId)
                    .query((rs, rowNum) -> rs.getObject("org_id", UUID.class))
                    .list());
        }
        return Optional.of(new UserAccount(row.userId(), row.fullName(), row.displayName(),
                row.email(), row.passwordHash(), role, managed));
    }
}
