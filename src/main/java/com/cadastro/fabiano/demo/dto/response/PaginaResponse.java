package com.cadastro.fabiano.demo.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envelope de resposta paginada da API.
 *
 * <p>Existe para os controllers nao devolverem {@code PageImpl} direto. O Spring
 * Data avisa, a cada resposta paginada, que serializar {@code PageImpl} nao tem
 * garantia de formato estavel entre versoes — e esse aviso era o campeao de
 * volume no log (FABIANO-25/36).</p>
 *
 * <p>Os campos sao exatamente os sete que o frontend le, na mesma grafia, entao
 * o JSON que sai daqui e compativel com o de antes e o Angular nao precisou
 * mudar. Campos que o {@code PageImpl} emitia e ninguem consumia — {@code
 * pageable}, {@code sort}, {@code numberOfElements}, {@code empty} — ficaram de
 * fora de proposito: menos bytes por resposta e menos contrato para manter.</p>
 *
 * <p>Ao adicionar um campo aqui, conferir antes se o front realmente precisa
 * dele. O valor deste envelope e ser um contrato pequeno e explicito.</p>
 */
public record PaginaResponse<T>(
        List<T> content,
        int totalPages,
        long totalElements,
        int number,
        int size,
        boolean first,
        boolean last
) {

    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(
                pagina.getContent(),
                pagina.getTotalPages(),
                pagina.getTotalElements(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.isFirst(),
                pagina.isLast());
    }
}
