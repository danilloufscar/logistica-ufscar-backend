package br.com.sglu.service;

import br.com.sglu.exception.EstoqueInsuficienteException;
import br.com.sglu.model.*;
import br.com.sglu.repository.MovimentacaoEstoqueRepository;
import br.com.sglu.repository.ProdutoRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DespachoEstoqueServiceTest {
    @Mock ProdutoRepository produtos;
    @Mock MovimentacaoEstoqueRepository movimentacoes;
    static final Instant AGORA = Instant.parse("2026-11-12T01:00:00Z");
    final Usuario usuario = new Usuario("Operador", "op@example.com", "hash", Perfil.OPERADOR);

    @Test
    void bloqueiaEmOrdemDeIdEGravaSaidasComDataDeSaoPaulo() {
        var a = produto(1L, 5); var b = produto(2L, 3);
        var pedido = pedido(b, a);
        when(produtos.buscarParaAtualizarEstoque(1L)).thenReturn(Optional.of(a));
        when(produtos.buscarParaAtualizarEstoque(2L)).thenReturn(Optional.of(b));
        new DespachoEstoqueService(produtos, movimentacoes).baixar(pedido, usuario, AGORA);
        var ordem = inOrder(produtos, movimentacoes);
        ordem.verify(produtos).buscarParaAtualizarEstoque(1L);
        ordem.verify(produtos).buscarParaAtualizarEstoque(2L);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<List<MovimentacaoEstoque>> captor = ArgumentCaptor.forClass((Class) List.class);
        ordem.verify(movimentacoes).saveAllAndFlush(captor.capture());
        assertThat(a.getSaldo()).isEqualTo(3); assertThat(b.getSaldo()).isEqualTo(1);
        assertThat(captor.getValue()).hasSize(2).allSatisfy(saida -> {
            assertThat(saida.getTipo()).isEqualTo(TipoMovimentacaoEstoque.SAIDA);
            assertThat(saida.getPedido()).isSameAs(pedido);
            assertThat(saida.getUsuario()).isSameAs(usuario);
            assertThat(saida.getData()).isEqualTo(LocalDate.of(2026, 11, 11));
            assertThat(saida.getRegistradoEm()).isEqualTo(AGORA);
        });
    }

    @Test
    void confereTodosOsProdutosAntesDeAlterarQualquerSaldo() {
        var a = produto(1L, 5); var b = produto(2L, 1);
        when(produtos.buscarParaAtualizarEstoque(1L)).thenReturn(Optional.of(a));
        when(produtos.buscarParaAtualizarEstoque(2L)).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> new DespachoEstoqueService(produtos, movimentacoes).baixar(pedido(a, b), usuario, AGORA))
                .isInstanceOfSatisfying(EstoqueInsuficienteException.class, erro -> {
                    assertThat(erro.getItens()).singleElement().satisfies(item -> {
                        assertThat(item.produtoId()).isEqualTo(2L);
                        assertThat(item.faltante()).isEqualTo(1);
                    });
                });
        assertThat(a.getSaldo()).isEqualTo(5); assertThat(b.getSaldo()).isEqualTo(1);
        verifyNoInteractions(movimentacoes);
    }

    @Test
    void dominioNaoPermiteSaidaInvalidaNemSaldoNegativo() {
        var produto = produto(1L, 2);
        assertThatThrownBy(() -> produto.registrarSaida(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> produto.registrarSaida(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> produto.registrarSaida(3)).isInstanceOf(EstoqueInsuficienteException.class);
        assertThat(produto.getSaldo()).isEqualTo(2);
        produto.registrarSaida(2);
        assertThat(produto.getSaldo()).isZero();
    }

    private Produto produto(long id, int saldo) {
        var produto = new Produto("P" + id, "Produto " + id, 0);
        ReflectionTestUtils.setField(produto, "id", id);
        produto.registrarEntrada(saldo);
        return produto;
    }
    private Pedido pedido(Produto a, Produto b) {
        return new Pedido("ABCD2345", new Cliente("Cliente", "123", "Rua fictícia"), "Rua fictícia",
                LocalDate.of(2026, 11, 13), List.of(new Pedido.Item(a, 2), new Pedido.Item(b, 2)), usuario, AGORA);
    }
}
