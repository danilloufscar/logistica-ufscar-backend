package br.com.sglu.repository;

import br.com.sglu.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    boolean existsByCodigo(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select produto from Produto produto where produto.id = :id")
    Optional<Produto> buscarParaAtualizarEstoque(@Param("id") Long id);
}
