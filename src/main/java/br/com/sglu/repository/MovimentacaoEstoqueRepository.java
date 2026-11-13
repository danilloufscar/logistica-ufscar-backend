package br.com.sglu.repository;

import br.com.sglu.model.MovimentacaoEstoque;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, Long> {

    @Query("""
            SELECT m FROM MovimentacaoEstoque m
            JOIN FETCH m.produto p
            JOIN FETCH m.usuario u
            LEFT JOIN FETCH m.pedido ped
            WHERE p.id = :produtoId
            ORDER BY m.data DESC, m.registradoEm DESC, m.id DESC
            """)
    List<MovimentacaoEstoque> buscarPorProdutoId(@Param("produtoId") Long produtoId);
}