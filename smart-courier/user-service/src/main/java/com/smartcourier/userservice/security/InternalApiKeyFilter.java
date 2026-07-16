package com.smartcourier.userservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcourier.userservice.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Guards all /internal/** paths by validating the X-Internal-Api-Key header.
 * On mismatch or absence: writes 401 ErrorResponse and stops the filter chain.
 * On match: continues the chain (no SecurityContext authentication needed for this path).
 * This filter only activates for requests whose path starts with /internal/.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final String internalApiKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(@Value("${internal.api-key}") String internalApiKey,
                                ObjectMapper objectMapper) {
        this.internalApiKey = internalApiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only activate for /internal/** paths
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String providedKey = request.getHeader(API_KEY_HEADER);

        if (providedKey == null || !providedKey.equals(internalApiKey)) {
            writeErrorResponse(response, request.getRequestURI(), HttpStatus.UNAUTHORIZED,
                    "Missing or invalid X-Internal-Api-Key header");
            return;
        }

        // Key is valid — continue; no SecurityContext needed for internal paths
        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response,
                                    String path,
                                    HttpStatus status,
                                    String message) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
