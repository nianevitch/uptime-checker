package com.isofuture.uptime.service;

import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * IdentityResolver - Determines unique identity of CSV rows for duplicate detection.
 * 
 * Currently uses email address as the identity key.
 * This can be extended in the future to support multiple rules (e.g., phone + name, etc.)
 */
@Component
public class IdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(IdentityResolver.class);

    /**
     * Common email field names that might be used in CSV files
     */
    private static final String[] EMAIL_FIELD_NAMES = {
        "email", "e-mail", "emailaddress", "email_address", 
        "mail", "emailid", "email_id", "contactemail", "contact_email"
    };

    /**
     * Extracts the identity key from a CSV row.
     * Currently uses email address as the identity.
     * 
     * @param row The CSV row data
     * @return The identity key (email address), or null if no email found
     */
    public String getIdentityKey(Map<String, String> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }

        // Try to find email field (case-insensitive)
        for (String emailField : EMAIL_FIELD_NAMES) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String key = entry.getKey().trim().toLowerCase();
                    String value = entry.getValue().trim().toLowerCase();
                    
                    if (key.equals(emailField) && isValidEmail(value)) {
                        log.debug("Found identity key (email): {} = {}", entry.getKey(), entry.getValue());
                        return normalizeEmail(value);
                    }
                }
            }
        }

        log.debug("No valid email found in row for identity resolution");
        return null;
    }

    /**
     * Normalizes email address for comparison (lowercase, trimmed)
     */
    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    /**
     * Basic email validation
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // Simple email validation - contains @ and at least one dot after @
        return email.contains("@") && email.indexOf("@") < email.lastIndexOf(".");
    }

    /**
     * Checks if two rows have the same identity
     */
    public boolean isSameIdentity(Map<String, String> row1, Map<String, String> row2) {
        String key1 = getIdentityKey(row1);
        String key2 = getIdentityKey(row2);
        
        if (key1 == null || key2 == null) {
            return false;
        }
        
        return Objects.equals(key1, key2);
    }
}

