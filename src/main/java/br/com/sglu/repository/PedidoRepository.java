package br.com.sglu.repository;

import br.com.sglu.model.Pedido;
import br.com.sglu.model.StatusPedido;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {
    boolean existsByCodigoConsulta(String codigoConsulta);

    @Override
    @EntityGraph(attributePaths = "cliente")
    List<Pedido> findAll(Specification<Pedido> filtro, Sort sort);

    static Specification<Pedido> filtro(StatusPedido status, Long clienteId) {
        return (root, query, criteria) -> {
            var condicoes = new ArrayList<Predicate>();
            if (status != null) condicoes.add(criteria.equal(root.get("status"), status));
            if (clienteId != null) condicoes.add(criteria.equal(root.get("cliente").get("id"), clienteId));
            return criteria.and(condicoes.toArray(Predicate[]::new));
        };
    }
}