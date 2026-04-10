package com.cadastro.fabiano.demo.config;

import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Injeta os campos @Value manualmente via ReflectionTestUtils
        ReflectionTestUtils.setField(jwtService, "secret",
                "MinhaChaveSuperSecretaMuitoForte123456AbcDefGhi");
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);

        user = User.builder()
                .id(1L)
                .username("fabiano")
                .password("hash")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("generateToken: gera token JWT não nulo")
    void generateToken_notNull() {
        String token = jwtService.generateToken(user, user.getId());
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("extractUsername: extrai username do token gerado")
    void extractUsername_success() {
        String token = jwtService.generateToken(user, user.getId());
        String username = jwtService.extractUsername(token);
        assertThat(username).isEqualTo("fabiano");
    }

    @Test
    @DisplayName("isTokenValid: valida token gerado para o mesmo usuário")
    void isTokenValid_success() {
        String token = jwtService.generateToken(user, user.getId());
        boolean valid = jwtService.isTokenValid(token, user);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("isTokenValid: retorna false para usuário diferente")
    void isTokenValid_differentUser_false() {
        String token = jwtService.generateToken(user, user.getId());

        User otherUser = User.builder()
                .id(2L).username("outro").password("hash")
                .role(Role.ROLE_ADMIN).active(true).build();

        boolean valid = jwtService.isTokenValid(token, otherUser);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("extractClaim: extrai claim customizada (userId)")
    void extractClaim_userId() {
        String token = jwtService.generateToken(user, 42L);
        Long userId = jwtService.extractClaim(token,
                claims -> claims.get("userId", Long.class));
        assertThat(userId).isEqualTo(42L);
    }
}
