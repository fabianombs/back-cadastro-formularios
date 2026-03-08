package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.UpdateUserRequest;
import com.cadastro.fabiano.demo.dto.response.UserResponse;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    // ==========================
    // Retorna todos os usuários ativos
    // ==========================
    @GetMapping
    public List<UserResponse> findAll() {
        return service.findAll();
    }

    // ==========================
    // Retorna apenas usuários com role CLIENT
    // ==========================
    @GetMapping("/clients")
    public List<UserResponse> findClients() {
        return service.findByRole(Role.ROLE_CLIENT);
    }

    // ==========================
    // Atualizar usuário
    // ==========================
    @PutMapping("/{id}")
    public void update(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request
    ) {
        service.update(id, request);
    }

    // ==========================
    // Desativar usuário
    // ==========================
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}