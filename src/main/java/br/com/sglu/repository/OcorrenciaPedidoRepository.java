package br.com.sglu.repository;

import br.com.sglu.model.OcorrenciaPedido;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OcorrenciaPedidoRepository extends JpaRepository<OcorrenciaPedido, Long> {

    @Query("""
            SELECT o FROM OcorrenciaPedido o
            JOIN FETCH o.usuario u
            WHERE o.pedido.id = :pedidoId
            ORDER BY o.dataHora ASC, o.id ASC
            """)
    List<OcorrenciaPedido> buscarPorPedidoId(@Param("pedidoId") Long pedidoId);
}