package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.entity.QuizFont;
import com.cadastro.fabiano.demo.repository.QuizFontRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuizFontServiceTest {

    private final QuizFontRepository repository = mock(QuizFontRepository.class);
    private final QuizFontService service = new QuizFontService(repository);

    private static MockMultipartFile file(String name, byte[] data) {
        return new MockMultipartFile("file", name, "font/woff", data);
    }

    @Test
    @DisplayName("store: aceita fonte e devolve caminho seguro")
    void store_ok() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        String url = service.store(file("../Latam Sans.woff", "wOFFxxxx".getBytes()));
        assertThat(url).matches("/quizzes/fonts/[0-9a-f-]{36}/[A-Za-z0-9._-]+").doesNotContain(" ");
        verify(repository).save(argThat((QuizFont f) -> f.getMimeType().equals("font/woff")));
    }

    @Test
    @DisplayName("store: recusa arquivo que não é fonte, vazio ou grande demais")
    void store_invalid() {
        assertThatThrownBy(() -> service.store(file("a.woff", "<html>".getBytes()))).hasMessageContaining("Formato");
        assertThatThrownBy(() -> service.store(file("a.woff", new byte[0]))).hasMessageContaining("Envie");
        assertThatThrownBy(() -> service.store(file("a.woff", new byte[(int) QuizFontService.MAX_BYTES + 1]))).hasMessageContaining("2 MB");
        verify(repository, never()).save(any());
    }
}
