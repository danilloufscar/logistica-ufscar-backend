package br.com.sglu.service;

import br.com.sglu.exception.CodigoProdutoJaCadastradoException;
import br.com.sglu.model.Produto;
import br.com.sglu.repository.ProdutoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {
    @Mock ProdutoRepository repository;
    @InjectMocks ProdutoService service;

    @Test
    void normalizaCodigoENomeEAceitaEstoqueMinimoZero() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var produto = service.cadastrar(" prod-001 ", " Produto Teste ", 0);
        assertThat(produto.getCodigo()).isEqualTo("PROD-001");
        assertThat(produto.getNome()).isEqualTo("Produto Teste");
        assertThat(produto.getEstoqueMinimo()).isZero();
        verify(repository).existsByCodigo("PROD-001");
        verify(repository).saveAndFlush(produto);
    }

    @Test
    void rejeitaCodigoDuplicadoAntesDePersistir() {
        when(repository.existsByCodigo("PROD-001")).thenReturn(true);
        assertThatThrownBy(() -> service.cadastrar(" prod-001 ", "Outro produto", 10))
                .isInstanceOf(CodigoProdutoJaCadastradoException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void listaPorNomeEId() {
        var produto = new Produto("P1", "Produto", 5);
        when(repository.findAll(Sort.by("nome", "id"))).thenReturn(List.of(produto));
        assertThat(service.listar()).containsExactly(produto);
    }

    @Test
    void dominioImpedeEstoqueMinimoNegativoMesmoForaDoController() {
        assertThatThrownBy(() -> service.cadastrar("P1", "Produto", -1)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
