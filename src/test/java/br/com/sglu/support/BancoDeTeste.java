package br.com.sglu.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Exclusivo dos testes: limpa filhos antes dos pais, independentemente da ordem das classes. */
public final class BancoDeTeste {
    private BancoDeTeste() {}

    public static void limpar(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM pedido_historico");
        jdbc.update("DELETE FROM pedido_itens");
        jdbc.update("DELETE FROM pedidos");
        jdbc.update("DELETE FROM produtos");
        jdbc.update("DELETE FROM clientes");
        jdbc.update("DELETE FROM usuarios");
    }
}
