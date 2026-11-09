package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Regulariza também bases em que Hibernate criou as tabelas antes da V3. Não remove dados. */
public class V4__regularizar_restricoes_pedidos extends BaseJavaMigration {
    private static final String STATUS = "status IN ('CRIADO', 'EM_SEPARACAO', 'DESPACHADO', 'EM_TRANSITO', 'ENTREGUE', 'CANCELADO')";

    @Override
    public Integer getChecksum() { return 1; }

    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        constraint(connection, "pedidos", "uk_pedidos_codigo_consulta", "UNIQUE (codigo_consulta)");
        normalizarUnicidadeCodigo(connection);
        constraint(connection, "pedidos", "fk_pedidos_cliente", "FOREIGN KEY (cliente_id) REFERENCES clientes (id)");
        constraint(connection, "pedidos", "ck_pedidos_codigo_consulta", "CHECK (CHAR_LENGTH(codigo_consulta) = 8)");
        constraint(connection, "pedidos", "ck_pedidos_status", "CHECK (" + STATUS + ")");
        constraint(connection, "pedido_itens", "fk_pedido_itens_produto", "FOREIGN KEY (produto_id) REFERENCES produtos (id)");
        constraint(connection, "pedido_itens", "uk_pedido_itens_produto", "UNIQUE (pedido_id, produto_id)");
        constraint(connection, "pedido_itens", "ck_pedido_itens_quantidade", "CHECK (quantidade > 0)");
        constraint(connection, "pedido_historico", "fk_pedido_historico_usuario", "FOREIGN KEY (usuario_id) REFERENCES usuarios (id)");
        constraint(connection, "pedido_historico", "ck_pedido_historico_status", "CHECK (" + STATUS + ")");
        cascadePedido(connection, "pedido_itens", "fk_pedido_itens_pedido");
        cascadePedido(connection, "pedido_historico", "fk_pedido_historico_pedido");
    }

    private static void constraint(Connection connection, String table, String name, String definition) throws SQLException {
        try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = ? AND LOWER(constraint_name) = ?")) {
            query.setString(1, connection.getSchema());
            query.setString(2, table);
            query.setString(3, name);
            try (var rows = query.executeQuery()) {
                rows.next();
                if (rows.getInt(1) > 0) return;
            }
        }
        execute(connection, "ALTER TABLE " + table + " ADD CONSTRAINT " + name + " " + definition);
    }

    private static void cascadePedido(Connection connection, String table, String name) throws SQLException {
        var metadata = connection.getMetaData();
        String metadataTable = metadata.storesUpperCaseIdentifiers() ? table.toUpperCase(java.util.Locale.ROOT) : table;
        var antigas = new ArrayList<String>();
        boolean cascadeExiste = false;
        try (var keys = metadata.getImportedKeys(connection.getCatalog(), connection.getSchema(), metadataTable)) {
            while (keys.next()) {
                if (!"pedido_id".equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        || !"pedidos".equalsIgnoreCase(keys.getString("PKTABLE_NAME"))) continue;
                if (keys.getShort("DELETE_RULE") == DatabaseMetaData.importedKeyCascade) cascadeExiste = true;
                else antigas.add(keys.getString("FK_NAME"));
            }
        }
        // Apenas as FKs de filhos para pedido mudam; apagar um usuário nunca apaga o histórico.
        for (String antiga : antigas) {
            execute(connection, "ALTER TABLE " + table + " DROP CONSTRAINT \"" + antiga.replace("\"", "\"\"") + "\"");
        }
        if (!cascadeExiste) constraint(connection, table, name, "FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE CASCADE");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static void normalizarUnicidadeCodigo(Connection connection) throws SQLException {
        var antigas = new ArrayList<String>();
        try (var query = connection.prepareStatement("SELECT tc.constraint_name FROM information_schema.table_constraints tc "
                + "JOIN information_schema.key_column_usage kc ON tc.constraint_schema=kc.constraint_schema AND tc.constraint_name=kc.constraint_name "
                + "AND tc.table_name=kc.table_name WHERE LOWER(tc.table_schema)=LOWER(?) AND LOWER(tc.table_name)='pedidos' "
                + "AND tc.constraint_type='UNIQUE' AND LOWER(tc.constraint_name)<>'uk_pedidos_codigo_consulta' "
                + "GROUP BY tc.constraint_name HAVING COUNT(*)=1 AND LOWER(MAX(kc.column_name))='codigo_consulta'")) {
            query.setString(1, connection.getSchema());
            try (var rows = query.executeQuery()) { while (rows.next()) antigas.add(rows.getString(1)); }
        }
        // A UNIQUE nomeada já foi instalada; remover a duplicada torna o erro identificável para retry.
        for (String antiga : antigas) execute(connection, "ALTER TABLE pedidos DROP CONSTRAINT \"" + antiga.replace("\"", "\"\"") + "\"");
    }
}
