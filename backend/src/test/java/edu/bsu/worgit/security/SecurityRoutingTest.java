package edu.bsu.worgit.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.domain.UserAccount;
import edu.bsu.worgit.repository.UserRepository;
import edu.bsu.worgit.support.RoutingProbeController;
import edu.bsu.worgit.support.TestData;

/** Each role reaches only its own area (spec section 3), the role comes from the database, errors are JSON. */
@WebMvcTest(controllers = RoutingProbeController.class)
@Import({ SecurityConfig.class, JwtService.class, JsonErrorWriter.class })
@TestPropertySource(properties = {
        "worgit.jwt.secret=" + TestData.SECRET,
        "worgit.jwt.ttl=8h",
        "worgit.jwt.issuer=worgit",
        "worgit.login.max-failures=5",
        "worgit.login.window=15m",
        "worgit.cors.allowed-origins=http://localhost:8081" })
class SecurityRoutingTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @MockitoBean
    UserRepository users;

    private String loginAs(UserAccount account) {
        when(users.findById(account.userId())).thenReturn(Optional.of(account));
        return "Bearer " + jwt.issue(account).token();
    }

    private UserAccount student() {
        return TestData.account("23-10457", Role.STUDENT, "x");
    }

    private UserAccount staff(String id, Role role) {
        return TestData.account(id, role, "x", UUID.randomUUID());
    }

    @Test
    void noTokenMeansUnauthenticated() throws Exception {
        mvc.perform(get("/api/shared/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void garbageTokenMeansUnauthenticated() throws Exception {
        mvc.perform(get("/api/student/probe").header("Authorization", "Bearer nonsense"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void studentReachesStudentAreaOnly() throws Exception {
        String auth = loginAs(student());
        mvc.perform(get("/api/student/probe").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(content().string("23-10457"));
        mvc.perform(get("/api/admin/probe").header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/shared/probe").header("Authorization", auth)).andExpect(status().isOk());
    }

    @Test
    void everyStaffRoleReachesTheAdminAreaOnly() throws Exception {
        for (Role role : new Role[] { Role.ORGANIZATION_FACILITATOR, Role.FACULTY_ADVISER, Role.SYSTEM_ADMINISTRATOR }) {
            String auth = loginAs(staff("ADM-" + role.ordinal(), role));
            mvc.perform(get("/api/admin/probe").header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(content().string(role.name()));
            mvc.perform(get("/api/student/probe").header("Authorization", auth))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void theRoleInTheDatabaseWinsOverTheRoleInTheToken() throws Exception {
        String studentToken = loginAs(student());
        // the owner promotes the same id to System Administrator in the database
        when(users.findById("23-10457")).thenReturn(Optional.of(staff("23-10457", Role.SYSTEM_ADMINISTRATOR)));
        mvc.perform(get("/api/student/probe").header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/probe").header("Authorization", studentToken))
                .andExpect(status().isOk()).andExpect(content().string("SYSTEM_ADMINISTRATOR"));
    }

    @Test
    void aDeletedAccountCannotUseItsToken() throws Exception {
        String auth = loginAs(student());
        when(users.findById("23-10457")).thenReturn(Optional.empty());
        mvc.perform(get("/api/student/probe").header("Authorization", auth))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void databaseDownDuringAuthenticationIsADataStoreError() throws Exception {
        String auth = loginAs(student());
        when(users.findById(anyString())).thenThrow(new DataAccessResourceFailureException("connection refused"));
        mvc.perform(get("/api/student/probe").header("Authorization", auth))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATA_STORE"))
                .andExpect(jsonPath("$.message").value("We could not reach the database. Try again."));
    }

    @Test
    void unknownRouteForALoggedInUserIsNotFound() throws Exception {
        String auth = loginAs(student());
        mvc.perform(get("/api/does-not-exist").header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
