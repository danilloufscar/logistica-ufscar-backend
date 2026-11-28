package br.com.sglu.repository;

import br.com.sglu.model.Pedido;
import br.com.sglu.model.StatusPedido;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Projeções de leitura: não carrega itens, clientes ou históricos completos. */
public interface RelatorioRepository extends Repository<Pedido, Long> {
    interface ContagemStatus {
        StatusPedido getStatus();
        long getQuantidade();
    }
    interface Entrega {
        LocalDate getPrazo();
        Instant getEntregueEm();
        Instant getCriadoEm();
    }

    @Query("""
            select p.status as status, count(p) as quantidade from Pedido p
            where p.criadoEm >= :inicio and p.criadoEm < :fim
              and p.status <> br.com.sglu.model.StatusPedido.CANCELADO
            group by p.status
            """)
    List<ContagemStatus> contarPorStatus(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

    @Query("""
            select p.prazoEntrega as prazo, h.dataHora as entregueEm, p.criadoEm as criadoEm
            from Pedido p join p.historico h
            where p.status = br.com.sglu.model.StatusPedido.ENTREGUE
              and h.status = br.com.sglu.model.StatusPedido.ENTREGUE
              and h.dataHora >= :inicio and h.dataHora < :fim
            """)
    List<Entrega> buscarEntregas(@Param("inicio") Instant inicio, @Param("fim") Instant fim);
}
