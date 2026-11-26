package edu.bsu.worgit.security;

import java.io.IOException;
import java.time.Clock;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.bsu.worgit.web.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;

/** Writes an ErrorResponse from the security layer, which sits outside Spring MVC's exception handling. */
@Component
public class JsonErrorWriter {

    private final ObjectMapper mapper;
    private final Clock clock;

    public JsonErrorWriter(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), new ErrorResponse(status, code, message, null, clock.instant()));
    }
}
