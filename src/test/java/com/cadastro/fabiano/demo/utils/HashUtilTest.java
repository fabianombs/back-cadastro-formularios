package com.cadastro.fabiano.demo.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    @Test
    @DisplayName("generateSHA256: retorna hash hex de 64 caracteres")
    void generateSHA256_returnsHex() {
        byte[] data = "hello".getBytes();
        String hash = HashUtil.generateSHA256(data);

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("generateSHA256: mesmo conteúdo gera mesmo hash")
    void generateSHA256_deterministic() {
        byte[] data = "test-data".getBytes();
        assertThat(HashUtil.generateSHA256(data)).isEqualTo(HashUtil.generateSHA256(data));
    }

    @Test
    @DisplayName("generateSHA256: conteúdos diferentes geram hashes diferentes")
    void generateSHA256_differentDataDifferentHash() {
        assertThat(HashUtil.generateSHA256("a".getBytes()))
                .isNotEqualTo(HashUtil.generateSHA256("b".getBytes()));
    }
}
