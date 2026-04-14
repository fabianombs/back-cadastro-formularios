package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.UpdateUserRequest;
import com.cadastro.fabiano.demo.dto.response.UserResponse;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@Tag(name = "Usuários", description = "Gerenciamento de usuários do sistema")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar todos os usuários")
    public Page<UserResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/clients")
    @Operation(summary = "Listar usuários com role CLIENT")
    public Page<UserResponse> findClients(Pageable pageable) {
        return service.findByRole(Role.ROLE_CLIENT, pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualizar usuário (somente ADMIN)")
    public void update(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request) {
        service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Excluir usuário (somente ADMIN)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}