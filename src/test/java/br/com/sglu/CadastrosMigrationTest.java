package br.com.sglu;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class CadastrosMigrationTest {
    @Test
    void migracaoPreservaUsuariosExistentesNaVersaoAnterior() {
        String url = "jdbc:h2:mem:sglu-migration-rf03;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(url);
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setMaximumPoolSize(3);
            var jdbc = new JdbcTemplate(dataSource);
            Flyway.configure().dataSource(dataSource).target("1").load().migrate();
            jdbc.update("INSERT INTO usuarios (nome, email, senha_hash, perfil) VALUES (?, ?, ?, ?)",
                    "Administrador existente", "existente@example.com", "hash-ficticio", "ADMINISTRADOR");

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(jdbc.queryForObject("SELECT nome FROM usuarios WHERE email = ?", String.class, "existente@example.com"))
                    .isEqualTo("Administrador existente");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM produtos", Integer.class)).isZero();
        }
    }
}
