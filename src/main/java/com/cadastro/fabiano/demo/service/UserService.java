package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.UpdateUserRequest;
import com.cadastro.fabiano.demo.dto.response.UserResponse;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public List<UserResponse> findAll() {

        return repository.findAll()
                .stream()
                .filter(User::getActive)
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getUsername(),
                        user.getRole().name()
                ))
                .toList();

    }

    public void update(Long id, UpdateUserRequest request) {

        User user = repository.findById(id)
                .orElseThrow();

        user.setName(request.name());
        user.setEmail(request.email());

        repository.save(user);

    }

    public void delete(Long id) {

        User user = repository.findById(id)
                .orElseThrow();

        user.setActive(false);

        repository.save(user);

    }

}