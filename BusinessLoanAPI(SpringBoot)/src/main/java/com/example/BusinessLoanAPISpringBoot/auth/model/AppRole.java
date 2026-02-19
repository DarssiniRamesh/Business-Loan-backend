package com.example.BusinessLoanAPISpringBoot.auth.model;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Role entity (RBAC).
 */
@Entity
@Table(name = "app_role")
public class AppRole {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 64)
    private RoleName name;

    public AppRole() {}

    public AppRole(UUID id, RoleName name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setName(RoleName name) {
        this.name = name;
    }
}
