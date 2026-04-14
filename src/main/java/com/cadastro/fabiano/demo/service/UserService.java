package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.UpdateUserRequest;
import com.cadastro.fabiano.demo.dto.response.UserResponse;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public Page<UserResponse> findAll(Pageable pageable) {
        return repository.findByActiveTrue(pageable)
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getUsername(),
                        user.getRole().name()
                ));
    }

    public Page<UserResponse> findByRole(Role role, Pageable pageable) {
        return repository.findByActiveTrueAndRole(role, pageable)
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getUsername(),
                        user.getRole().name()
                ));
    }

    public void update(Long id, UpdateUserRequest request) {

        User user = repository.findById(id)
                .orElseThrow();

        user.setName(request.name());
        user.setEmail(request.email());

        if (request.role() != null) {
            user.setRole(Role.valueOf(request.role()));
        }

        repository.save(user);

    }

    public void delete(Long id) {

        User user = repository.findById(id)
                .orElseThrow();

        user.setActive(false);

        repository.save(user);

    }

}