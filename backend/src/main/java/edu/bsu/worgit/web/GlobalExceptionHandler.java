package edu.bsu.worgit.web;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import edu.bsu.worgit.domain.Messages;
import edu.bsu.worgit.exception.ApiException;

/** Turns every failure into the one ErrorResponse shape (spec section 8 mapping). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        if (ex.getCause() != null) {
            log.error("{} caused by", ex.code(), ex.getCause());
        }
        return build(ex.httpStatus(), ex.code(), ex.getMessage(), ex.field());
    }

    /** Any database failure that reaches the web layer: DataStoreException semantics (503 -> SYS-03). */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex) {
        log.error("Database failure", ex);
        return build(503, "DATA_STORE", Messages.DATA_STORE, null);
    }

    /** Malformed requests -> SYS-02 (ASSUMED to include wrong method / media type). */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ServletRequestBindingException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return build(400, "INVALID_REQUEST", Messages.INVALID_REQUEST, null);
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        return build(404, "NOT_FOUND", Messages.NOT_FOUND, null);
    }

    // Spring Security exceptions thrown inside controllers must not fall into the catch-all below.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(Exception ex) {
        return build(403, "ACCESS_DENIED", Messages.ACCESS_DENIED, null);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(Exception ex) {
        return build(401, "UNAUTHENTICATED", Messages.UNAUTHENTICATED, null);
    }

    /** Anything unexpected: 500 SERVICE_ERROR (ASSUMED; the app shows SYS-03). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(500, "SERVICE_ERROR", Messages.SERVICE_ERROR, null);
    }

    private ResponseEntity<ErrorResponse> build(int status, String code, String message, String field) {
        return ResponseEntity.status(status).body(new ErrorResponse(status, code, message, field, clock.instant()));
    }
}
