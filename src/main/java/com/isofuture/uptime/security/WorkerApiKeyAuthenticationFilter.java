package com.isofuture.uptime.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.isofuture.uptime.service.WorkerApiKeyService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class WorkerApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_ENDPOINTS = Set.of(
        "/api/checks/next",
        "/api/checks/result",
        "/api/checks/pending"
    );

    private final WorkerApiKeyService workerApiKeyService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public WorkerApiKeyAuthenticationFilter(WorkerApiKeyService workerApiKeyService) {
        this.workerApiKeyService = workerApiKeyService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (requiresWorkerApiKey(request)) {
            String apiKey = request.getHeader(WorkerApiKeyService.HEADER_NAME);
            try {
                workerApiKeyService.assertValid(apiKey);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    new WorkerPrincipal("worker-api"),
                    null,
                    Set.of(new SimpleGrantedAuthority("ROLE_WORKER"))
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid worker API key");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresWorkerApiKey(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!StringUtils.hasText(path)) {
            return false;
        }

        for (String endpoint : PROTECTED_ENDPOINTS) {
            if (pathMatcher.match(endpoint, path)) {
                // restrict to POST for /next and PATCH for /result
                if (endpoint.endsWith("/next") && "POST".equalsIgnoreCase(method)) {
                    return true;
                }
                if (endpoint.endsWith("/result") && "PATCH".equalsIgnoreCase(method)) {
                    return true;
                }
                if (endpoint.endsWith("/pending") && "GET".equalsIgnoreCase(method)) {
                    return true;
                }
            }
        }

        return false;
    }
}


