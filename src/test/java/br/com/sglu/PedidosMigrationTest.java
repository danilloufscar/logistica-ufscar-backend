package br.com.sglu;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidosMigrationTest {
    @Test
    void migracaoDeSaidasPreservaEntradasESaldosSemBaixaRetroativaEProtegeVinculos() throws Exception {
        try (var banco = new Banco()) {
            banco.legado(1);
            banco.migrar("5");
            banco.jdbc.update("UPDATE produtos SET saldo=12 WHERE id=1");
            banco.jdbc.update("UPDATE pedidos SET status='DESPACHADO' WHERE id=1");
            banco.jdbc.update("INSERT INTO movimentacoes_estoque(produto_id,tipo,quantidade,data,registrado_em,usuario_id) VALUES(1,'ENTRADA',12,CURRENT_DATE,CURRENT_TIMESTAMP,1)");
            banco.migrar(null);
            assertThat(banco.jdbc.queryForObject("SELECT saldo FROM produtos WHERE id=1", Integer.class)).isEqualTo(12);
            assertThat(banco.jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque WHERE tipo='ENTRADA' AND pedido_id IS NULL", Integer.class)).isEqualTo(1);
            assertThat(banco.jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque WHERE tipo='SAIDA'", Integer.class)).isZero();
            String inserir = "INSERT INTO movimentacoes_estoque(produto_id,tipo,quantidade,data,registrado_em,usuario_id,pedido_id) VALUES(1,?,1,CURRENT_DATE,CURRENT_TIMESTAMP,1,?)";
            assertThatThrownBy(() -> banco.jdbc.update(inserir, "SAIDA", null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> banco.jdbc.update(inserir, "ENTRADA", 1)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> banco.jdbc.update(inserir, "SAIDA", 9999)).isInstanceOf(DataIntegrityViolationException.class);
            banco.jdbc.update(inserir, "SAIDA", 1);
            assertThatThrownBy(() -> banco.jdbc.update(inserir, "SAIDA", 1)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> banco.jdbc.update("DELETE FROM pedidos WHERE id=1")).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void migracaoDeEstoquePreservaProdutosExistentesEInicializaSaldoZero() throws Exception {
        try (var banco = new Banco()) {
            banco.migrar("4");
            banco.jdbc.update("INSERT INTO produtos(codigo,nome,estoque_minimo) VALUES('EXISTENTE','Produto existente',8)");
            banco.migrar(null);
            assertThat(banco.jdbc.queryForObject("SELECT saldo FROM produtos WHERE codigo='EXISTENTE'", Integer.class)).isZero();
            assertThat(banco.jdbc.queryForObject("SELECT estoque_minimo FROM produtos WHERE codigo='EXISTENTE'", Integer.class)).isEqualTo(8);
            assertThat(banco.jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque", Integer.class)).isZero();
        }
    }

    @Test
    void instalaTodasAsMigracoesEmBaseNova() throws Exception {
        try (var banco = new Banco()) {
            banco.migrar(null);
            assertThat(banco.jdbc.queryForObject("SELECT COUNT(*) FROM pedidos", Integer.class)).isZero();
            assertThat(banco.flyway(null).info().current().getVersion().toString()).isEqualTo("6");
        }
    }

    @Test
    void regularizaBaseLegadaPreservandoPedidoItensHistoricoEUsuario() throws Exception {
        try (var banco = new Banco()) {
            banco.legado(2);
            banco.migrar(null);
            assertThat(banco.jdbc.queryForObject("SELECT codigo_consulta FROM pedidos WHERE id=1", String.class)).isEqualTo("ABCD2345");
            assertThat(banco.jdbc.queryForObject("SELECT quantidade FROM pedido_itens WHERE id=1", Integer.class)).isEqualTo(2);
            assertThat(banco.jdbc.queryForObject("SELECT COUNT(*) FROM pedido_historico", Integer.class)).isEqualTo(1);
            assertThatThrownBy(() -> banco.jdbc.update("DELETE FROM usuarios WHERE id=1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> banco.jdbc.update("UPDATE pedido_itens SET quantidade=0 WHERE id=1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> banco.jdbc.update("UPDATE pedidos SET codigo_consulta='ABC' WHERE id=1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> banco.jdbc.update("UPDATE pedidos SET status='INVALIDO' WHERE id=1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            banco.jdbc.update("DELETE FROM pedidos WHERE id=1");
            assertThat(banco.jdbc.queryForObject("SELECT COUNT(*) FROM pedido_itens", Integer.class)).isZero();
            assertThat(banco.jdbc.queryForObject("SELECT COUNT(*) FROM pedido_historico", Integer.class)).isZero();
            assertThat(banco.jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class)).isEqualTo(1);
            banco.migrar(null); // Reiniciar não reaplica nem modifica os registros preservados.
        }
    }

    @Test
    void dadosLegadosInvalidosBloqueiamMigracaoSemApagarRegistros() throws Exception {
        try (var banco = new Banco()) {
            banco.legado(0);
            assertThatThrownBy(() -> banco.migrar(null)).isInstanceOf(FlywayException.class);
            assertThat(banco.jdbc.queryForObject("SELECT quantidade FROM pedido_itens WHERE id=1", Integer.class)).isZero();
            assertThat(banco.jdbc.queryForObject("SELECT COUNT(*) FROM pedidos", Integer.class)).isEqualTo(1);
        }
    }

    private static class Banco implements AutoCloseable {
        final String schema = "teste_migracao_" + UUID.randomUUID().toString().replace("-", "");
        final HikariDataSource source = new HikariDataSource();
        final JdbcTemplate jdbc;

        Banco() throws Exception {
            String url = System.getenv("SGLU_MIGRATION_TEST_URL");
            boolean postgres = url != null;
            if (!postgres) url = "jdbc:h2:mem:" + schema + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
            String user = postgres ? System.getenv("SPRING_DATASOURCE_USERNAME") : "sa";
            String password = postgres ? System.getenv("SPRING_DATASOURCE_PASSWORD") : "";
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA \"" + schema + "\"");
            }
            source.setJdbcUrl(url);
            source.setUsername(user);
            source.setPassword(password);
            source.setSchema(schema);
            source.setMaximumPoolSize(3);
            jdbc = new JdbcTemplate(source);
        }

        Flyway flyway(String target) {
            var config = Flyway.configure().dataSource(source).defaultSchema(schema).validateMigrationNaming(true);
            if (target != null) config.target(target);
            return config.load();
        }

        void migrar(String target) { flyway(target).migrate(); }

        void legado(int quantidade) {
            migrar("2");
            jdbc.execute("CREATE TABLE pedidos (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, codigo_consulta VARCHAR(8) NOT NULL UNIQUE, cliente_id BIGINT NOT NULL REFERENCES clientes(id), endereco_entrega VARCHAR(500) NOT NULL, prazo_entrega DATE NOT NULL, status VARCHAR(20) NOT NULL, criado_em TIMESTAMP WITH TIME ZONE NOT NULL, versao BIGINT NOT NULL)");
            jdbc.execute("CREATE TABLE pedido_itens (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, pedido_id BIGINT NOT NULL REFERENCES pedidos(id), produto_id BIGINT NOT NULL REFERENCES produtos(id), quantidade INTEGER NOT NULL)");
            jdbc.execute("CREATE TABLE pedido_historico (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, pedido_id BIGINT NOT NULL REFERENCES pedidos(id), status VARCHAR(20) NOT NULL, data_hora TIMESTAMP WITH TIME ZONE NOT NULL, usuario_id BIGINT NOT NULL REFERENCES usuarios(id))");
            jdbc.update("INSERT INTO usuarios(id,nome,email,senha_hash,perfil) VALUES(1,'Teste','teste@example.com','hash','OPERADOR')");
            jdbc.update("INSERT INTO clientes(id,nome,telefone,endereco) VALUES(1,'Cliente','123','Rua fictícia')");
            jdbc.update("INSERT INTO produtos(id,codigo,nome,estoque_minimo) VALUES(1,'P1','Produto',0)");
            jdbc.update("INSERT INTO pedidos VALUES(1,'ABCD2345',1,'Rua fictícia',CURRENT_DATE,'CRIADO',CURRENT_TIMESTAMP,0)");
            jdbc.update("INSERT INTO pedido_itens VALUES(1,1,1,?)", quantidade);
            jdbc.update("INSERT INTO pedido_historico VALUES(1,1,'CRIADO',CURRENT_TIMESTAMP,1)");
        }

        @Override
        public void close() {
            try { jdbc.execute("DROP SCHEMA \"" + schema + "\" CASCADE"); }
            finally { source.close(); }
        }
    }
}
