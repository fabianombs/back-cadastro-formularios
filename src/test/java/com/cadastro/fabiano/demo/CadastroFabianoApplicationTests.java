package com.cadastro.fabiano.demo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Teste de carregamento do contexto Spring.
 * Desabilitado por padrão pois requer conexão com banco de dados MySQL.
 * Para executar: remova @Disabled e configure as variáveis de ambiente do banco.
 */
@SpringBootTest
@Disabled("Requer banco de dados MySQL em execução")
class CadastroFabianoApplicationTests {

	@Test
	void contextLoads() {
	}

}
