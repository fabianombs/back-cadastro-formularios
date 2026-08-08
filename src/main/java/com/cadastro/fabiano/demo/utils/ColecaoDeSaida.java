package com.cadastro.fabiano.demo.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copia coleções do JPA antes de entregá-las a um DTO.
 *
 * <p>Uma coleção mapeada com {@code @ElementCollection} ou {@code @OneToMany}
 * nao e um {@code List} comum: e um proxy do Hibernate que so vai ao banco na
 * primeira vez que alguem o percorre. Entregar esse proxy dentro de um DTO
 * significa que a ida ao banco vai acontecer onde o DTO for lido — e, com
 * {@code open-in-view=false}, isso e na serializacao do JSON, com a sessao ja
 * fechada. O resultado e {@code LazyInitializationException} disfarcada de
 * {@code HttpMessageNotWritableException}, em tempo de execucao.</p>
 *
 * <p>Testar {@code != null} nao resolve: o proxy nunca e nulo. So percorrer
 * resolve, e e isso que a copia faz — dentro da transacao, onde e barato.</p>
 *
 * <p>Descoberto no FABIANO-37, em quatro mapeadores de uma vez.</p>
 */
public final class ColecaoDeSaida {

    private ColecaoDeSaida() {
    }

    public static <T> List<T> lista(List<T> doBanco) {
        return doBanco == null ? List.of() : new ArrayList<>(doBanco);
    }

    // LinkedHashMap e nao Map.copyOf porque a coluna de valor e TEXT anulavel,
    // e Map.copyOf recusa valor nulo com NullPointerException.
    public static <K, V> Map<K, V> mapa(Map<K, V> doBanco) {
        return doBanco == null ? Map.of() : new LinkedHashMap<>(doBanco);
    }
}
