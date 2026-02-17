package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// PUBLIC_INTERFACE
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
