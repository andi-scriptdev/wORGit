package edu.bsu.worgit.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpInputMessage;

import edu.bsu.worgit.exception.DuplicateMembershipException;
import edu.bsu.worgit.exception.InvalidProfileException;
import edu.bsu.worgit.support.TestClock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new TestClock());

    private static void check(ResponseEntity<ErrorResponse> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(status);
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void apiExceptionsKeepTheirStatusCodeMessageAndField() {
        var response = handler.handleApi(new InvalidProfileException("Bio must be 150 characters or fewer", "bio"));
        check(response, 400, "INVALID_PROFILE");
        assertThat(response.getBody().message()).isEqualTo("Bio must be 150 characters or fewer");
        assertThat(response.getBody().field()).isEqualTo("bio");

        check(handler.handleApi(new DuplicateMembershipException("Developers Guild")), 409, "DUPLICATE_MEMBERSHIP");
    }

    @Test
    void databaseFailuresBecomeDataStoreErrors() {
        var response = handler.handleDataAccess(new DataAccessResourceFailureException("connection refused"));
        check(response, 503, "DATA_STORE");
        assertThat(response.getBody().message()).isEqualTo("We could not reach the database. Try again.");
    }

    @Test
    void malformedRequestsBecomeInvalidRequest() {
        check(handler.handleBadRequest(new HttpMessageNotReadableException("bad json", (HttpInputMessage) null)),
                400, "INVALID_REQUEST");
    }

    @Test
    void unknownRoutesBecomeNotFound() {
        check(handler.handleNotFound(new RuntimeException("no handler")), 404, "NOT_FOUND");
    }

    @Test
    void securityExceptionsKeepTheirMeaning() {
        check(handler.handleAccessDenied(new org.springframework.security.access.AccessDeniedException("no")),
                403, "ACCESS_DENIED");
        check(handler.handleUnauthenticated(
                new org.springframework.security.authentication.BadCredentialsException("no")),
                401, "UNAUTHENTICATED");
    }

    @Test
    void anythingElseIsAServiceErrorWithoutDetails() {
        var response = handler.handleUnexpected(new IllegalStateException("secret internal detail"));
        check(response, 500, "SERVICE_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}
