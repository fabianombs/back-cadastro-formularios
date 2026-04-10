package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.UpdateUserRequest;
import com.cadastro.fabiano.demo.dto.response.UserResponse;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @InjectMocks
    private UserService service;

    private User buildUser(Long id, Role role) {
        return User.builder()
                .id(id)
                .name("Usuário " + id)
                .email("user" + id + "@email.com")
                .username("user" + id)
                .role(role)
                .active(true)
                .build();
    }

    // ─── findAll ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll: retorna usuários ativos paginados")
    void findAll_success() {
        User user = buildUser(1L, Role.ROLE_ADMIN);
        PageRequest pageable = PageRequest.of(0, 10);

        when(repository.findByActiveTrue(pageable)).thenReturn(new PageImpl<>(List.of(user)));

        Page<UserResponse> result = service.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).username()).isEqualTo("user1");
        assertThat(result.getContent().get(0).role()).isEqualTo("ROLE_ADMIN");
    }

    // ─── findByRole ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByRole: retorna usuários com role CLIENT")
    void findByRole_success() {
        User client = buildUser(2L, Role.ROLE_CLIENT);
        PageRequest pageable = PageRequest.of(0, 10);

        when(repository.findByActiveTrueAndRole(Role.ROLE_CLIENT, pageable))
                .thenReturn(new PageImpl<>(List.of(client)));

        Page<UserResponse> result = service.findByRole(Role.ROLE_CLIENT, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).role()).isEqualTo("ROLE_CLIENT");
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: atualiza nome e e-mail do usuário")
    void update_success() {
        User user = buildUser(1L, Role.ROLE_ADMIN);
        UpdateUserRequest request = new UpdateUserRequest("Novo Nome", "novo@email.com");

        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenReturn(user);

        service.update(1L, request);

        assertThat(user.getName()).isEqualTo("Novo Nome");
        assertThat(user.getEmail()).isEqualTo("novo@email.com");
        verify(repository).save(user);
    }

    @Test
    @DisplayName("update: lança exceção se usuário não encontrado")
    void update_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> service.update(99L, new UpdateUserRequest("X", "x@x.com")));
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: desativa usuário (soft delete via active=false)")
    void delete_success() {
        User user = buildUser(1L, Role.ROLE_ADMIN);

        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenReturn(user);

        service.delete(1L);

        assertThat(user.getActive()).isFalse();
        verify(repository).save(user);
    }

    @Test
    @DisplayName("delete: lança exceção se usuário não encontrado")
    void delete_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> service.delete(99L));
    }
}
