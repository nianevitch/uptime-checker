package com.isofuture.uptime.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * User - Represents a user in the system.
 * 
 * IMPORTANT: User records are NEVER physically deleted from the database.
 * Deletion is done via soft delete by setting the deletedAt timestamp.
 * All queries filter by deletedAt IS NULL to exclude soft-deleted users.
 */
@Entity
@Table(
    name = "user",
    indexes = {
        @Index(name = "IX_user_deleted_at", columnList = "deleted_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_user_email", columnNames = "email")
    }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_role",
        indexes = {
            @Index(name = "IX_user_role_user_id", columnList = "user_id"),
            @Index(name = "IX_user_role_role_id", columnList = "role_id")
        },
        joinColumns = @JoinColumn(
            name = "user_id",
            foreignKey = @ForeignKey(name = "FK_user_role_user_id")
        ),
        inverseJoinColumns = @JoinColumn(
            name = "role_id",
            foreignKey = @ForeignKey(name = "FK_user_role_role_id")
        )
    )
    private Set<Role> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_tier",
        indexes = {
            @Index(name = "IX_user_tier_user_id", columnList = "user_id"),
            @Index(name = "IX_user_tier_tier_id", columnList = "tier_id")
        },
        joinColumns = @JoinColumn(
            name = "user_id",
            foreignKey = @ForeignKey(name = "FK_user_tier_user_id")
        ),
        inverseJoinColumns = @JoinColumn(
            name = "tier_id",
            foreignKey = @ForeignKey(name = "FK_user_tier_tier_id")
        )
    )
    private Set<Tier> tiers = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public Set<Tier> getTiers() {
        return tiers;
    }

    public void setTiers(Set<Tier> tiers) {
        this.tiers = tiers;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

