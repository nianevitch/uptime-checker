package com.isofuture.uptime.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = args.length > 0 ? args[0] : "pass";
        String hash = encoder.encode(password);
        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash: " + hash);
        System.out.println("\nSQL Update:");
        System.out.println("UPDATE `users` SET `password_hash` = '" + hash + "' WHERE `email` IN ('mary@invoken.com', 'zookeeper@invoken.com');");
    }
}

