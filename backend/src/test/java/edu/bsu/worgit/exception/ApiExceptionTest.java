package edu.bsu.worgit.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.Messages;

/** Spec section 8: status, code and exact message of every exception. */
class ApiExceptionTest {

    private static void check(ApiException e, int status, String code, String message) {
        assertThat(e.httpStatus()).as("status of " + code).isEqualTo(status);
        assertThat(e.code()).isEqualTo(code);
        assertThat(e.getMessage()).isEqualTo(message);
    }

    @Test
    void validationFamily() {
        check(new InvalidInputException("x"), 400, "INVALID_INPUT", "x");
        check(new InvalidPasswordException(), 400, "INVALID_PASSWORD", "Password must be at least 8 characters");
        check(new AuthenticationException(), 401, "AUTHENTICATION_FAILED", "ID or password is incorrect");
        check(new InvalidMessageException("Message cannot be empty."), 400, "INVALID_MESSAGE",
                "Message cannot be empty.");
        check(new RoomAccessException(Messages.roomAccess("Developers Guild")), 403, "ROOM_ACCESS",
                "You need to be a member of Developers Guild to enter this room.");
        check(new InvalidProfileException(Messages.BIO_TOO_LONG, "bio"), 400, "INVALID_PROFILE",
                "Bio must be 150 characters or fewer");
        check(new DuplicateMembershipException("Developers Guild"), 409, "DUPLICATE_MEMBERSHIP",
                "You are already a member of Developers Guild.");
        check(new CertificateNotEligibleException("Sam Perez"), 422, "CERTIFICATE_NOT_ELIGIBLE",
                "Sam Perez has no recorded attendance, so no certificate was created.");
    }

    @Test
    void otherErrors() {
        check(new DataStoreException(new RuntimeException("down")), 503, "DATA_STORE",
                "We could not reach the database. Try again.");
        check(new NotFoundException(), 404, "NOT_FOUND", "Page not found.");
        check(new ForbiddenException(), 403, "ACCESS_DENIED", "You do not have access to this.");
        check(new TooManyAttemptsException(), 429, "TOO_MANY_ATTEMPTS",
                "Too many attempts. Try again in a few minutes.");
    }

    @Test
    void hierarchyFollowsTheClassDiagram() {
        assertThat(InvalidInputException.class).isAssignableFrom(InvalidPasswordException.class);
        assertThat(InvalidInputException.class).isAssignableFrom(AuthenticationException.class);
        assertThat(InvalidInputException.class).isAssignableFrom(InvalidMessageException.class);
        assertThat(InvalidInputException.class).isAssignableFrom(RoomAccessException.class);
        assertThat(InvalidInputException.class).isAssignableFrom(InvalidProfileException.class);
        assertThat(InvalidInputException.class).isAssignableFrom(DuplicateMembershipException.class);
        assertThat(InvalidInputException.class).isAssignableFrom(CertificateNotEligibleException.class);
        assertThat(InvalidInputException.class.isAssignableFrom(DataStoreException.class)).isFalse();
    }

    @Test
    void fieldsPointAtTheFormInput() {
        assertThat(new InvalidPasswordException().field()).isEqualTo("newPassword");
        assertThat(new InvalidMessageException("x").field()).isEqualTo("message");
        assertThat(new InvalidProfileException("x", "bio").field()).isEqualTo("bio");
        assertThat(new AuthenticationException().field()).isNull();
    }
}
