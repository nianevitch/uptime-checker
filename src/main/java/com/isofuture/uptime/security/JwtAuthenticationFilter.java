package com.isofuture.uptime.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtTokenProvider tokenProvider;
    private final DatabaseUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
        JwtTokenProvider tokenProvider,
        DatabaseUserDetailsService userDetailsService
    ) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            log.trace("JWT token found in request to {}", request.getRequestURI());
            if (tokenProvider.validate(token)) {
                try {
                    Claims claims = tokenProvider.parseClaims(token);
                    String username = claims.getSubject();
                    if (username != null) {
                        log.debug("Authenticating user: {}", username);
                        SecurityUser userDetails = (SecurityUser) userDetailsService.loadUserByUsername(username);
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                        );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("User authenticated successfully: {} (ID: {})", username, userDetails.getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to authenticate user from JWT token: {}", e.getMessage());
                    // If user cannot be loaded (e.g., email changed, user deleted), clear context and continue
                    // The request will be handled as unauthenticated
                    SecurityContextHolder.clearContext();
                }
            } else {
                log.debug("Invalid JWT token in request to {}", request.getRequestURI());
            }
        } else {
            log.trace("No JWT token found in request to {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}

