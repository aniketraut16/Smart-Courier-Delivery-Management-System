package com.smartcourier.userservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcourier.userservice.dto.ErrorResponse;
import com.smartcourier.userservice.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the Authorization: Bearer <token> header.
 * <ul>
 *   <li>Present + valid → populate SecurityContextHolder with userId principal and ROLE_X authority.</li>
 *   <li>Present + invalid → respond 401 ErrorResponse and stop the chain.</li>
 *   <li>Absent → continue unauthenticated (permitAll routes still work).</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = "Authorization";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTH_HEADER);

        // No Authorization header — continue unauthenticated (permitAll routes still served)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtService.isTokenValid(token)) {
            writeErrorResponse(response, request.getRequestURI(), HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token");
            return;
        }

        // Token is valid — extract claims and set SecurityContext
        UUID userId = jwtService.extractUserId(token);
        Role role = jwtService.extractRole(token);

        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.name());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(authority));

        SecurityContextHolder.getContext().setAuthentication(authentication);
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
