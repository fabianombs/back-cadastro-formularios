package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Page<User> findByActiveTrue(Pageable pageable);

    Page<User> findByActiveTrueAndRole(Role role, Pageable pageable);

}