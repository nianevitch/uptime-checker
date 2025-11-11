package com.isofuture.uptime.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkerApiKeyService {

    public static final String HEADER_NAME = "X-API-Key";

    private final Set<String> allowedKeys;

    public WorkerApiKeyService(@Value("${app.worker.api-keys:}") String rawKeys) {
        this.allowedKeys = Arrays.stream(rawKeys.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.toUnmodifiableSet());
    }

    public void assertValid(String providedKey) {
        if (allowedKeys.isEmpty()) {
            throw new AccessDeniedException("Worker API keys are not configured");
        }

        if (!StringUtils.hasText(providedKey) || !allowedKeys.contains(providedKey)) {
            throw new AccessDeniedException("Invalid worker API key");
        }
    }
}


