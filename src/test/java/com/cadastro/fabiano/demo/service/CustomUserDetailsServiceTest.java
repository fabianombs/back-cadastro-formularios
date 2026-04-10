package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    @DisplayName("loadUserByUsername: carrega usuário com authorities corretas")
    void loadUserByUsername_success() {
        User user = User.builder()
                .id(1L)
                .username("fabiano")
                .password("hash")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build();

        when(userRepository.findByUsername("fabiano")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("fabiano");

        assertThat(details.getUsername()).isEqualTo("fabiano");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.getAuthorities()).hasSize(1);
        assertThat(details.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("loadUserByUsername: lança UsernameNotFoundException se não encontrado")
    void loadUserByUsername_notFound_throws() {
        when(userRepository.findByUsername("naoexiste")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("naoexiste"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
