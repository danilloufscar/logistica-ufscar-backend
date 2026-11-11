package br.com.sglu.service;

import br.com.sglu.dto.estoque.EntradaEstoqueRequest;
import br.com.sglu.exception.DadosInvalidosException;
import br.com.sglu.exception.OperacaoNaoPermitidaException;
import br.com.sglu.exception.SessaoInvalidaException;
import br.com.sglu.model.*;
import br.com.sglu.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {
    static final Instant AGORA = Instant.parse("2026-11-09T15:00:00Z");
    static final LocalDate DATA = LocalDate.of(2026, 11, 8);
    @Mock ProdutoRepository produtos;
    @Mock MovimentacaoEstoqueRepository movimentacoes;
    @Mock UsuarioRepository usuarios;
    EstoqueService service;
    Produto produto;
    Usuario usuario;

    @BeforeEach
    void preparar() {
        service = new EstoqueService(produtos, movimentacoes, usuarios, Clock.fixed(AGORA, ZoneOffset.UTC));
        produto = new Produto("P1", "Produto", 2);
        usuario = new Usuario("Operador", "op@example.com", "hash", Perfil.OPERADOR);
        ReflectionTestUtils.setField(produto, "id", 1L);
        ReflectionTestUtils.setField(usuario, "id", 2L);
    }

    @Test
    void somaSaldoERegistraDataInformadaAutorEInstanteDoServidor() {
        referenciasValidas();
        when(movimentacoes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var primeira = service.registrarEntrada(new EntradaEstoqueRequest(1L, 10, DATA), 2L);
        var segunda = service.registrarEntrada(new EntradaEstoqueRequest(1L, 5, DATA), 2L);
        assertThat(primeira.saldoAtual()).isEqualTo(10);
        assertThat(segunda.saldoAtual()).isEqualTo(15);
        assertThat(segunda.data()).isEqualTo(DATA);
        assertThat(segunda.registradoEm()).isEqualTo(AGORA);
        assertThat(segunda.usuario().id()).isEqualTo(2L);
        assertThat(segunda.tipo()).isEqualTo(TipoMovimentacaoEstoque.ENTRADA);
        verify(produtos, times(2)).buscarParaAtualizarEstoque(1L);
    }

    @Test
    void produtoInexistenteGeraErroNoCampoSemMovimentacao() {
        when(usuarios.findById(2L)).thenReturn(Optional.of(usuario));
        assertThatThrownBy(() -> service.registrarEntrada(new EntradaEstoqueRequest(1L, 10, DATA), 2L))
                .isInstanceOfSatisfying(DadosInvalidosException.class,
                        erro -> assertThat(erro.getDetalhes()).containsEntry("produtoId", "Produto não encontrado"));
        verifyNoInteractions(movimentacoes);
    }

    @Test
    void sessaoSemUsuarioNaoAlteraEstoque() {
        assertThatThrownBy(() -> service.registrarEntrada(new EntradaEstoqueRequest(1L, 10, DATA), 2L))
                .isInstanceOf(SessaoInvalidaException.class);
        verifyNoInteractions(produtos, movimentacoes);
    }

    @Test
    void dominioRecusaQuantidadeNaoPositiva() {
        referenciasValidas();
        assertThatThrownBy(() -> service.registrarEntrada(new EntradaEstoqueRequest(1L, 0, DATA), 2L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(produto.getSaldo()).isZero();
        verifyNoInteractions(movimentacoes);
    }

    @Test
    void overflowNaoModificaSaldoNemCriaMovimentacao() {
        referenciasValidas();
        produto.registrarEntrada(Integer.MAX_VALUE);
        assertThatThrownBy(() -> service.registrarEntrada(new EntradaEstoqueRequest(1L, 1, DATA), 2L))
                .isInstanceOfSatisfying(OperacaoNaoPermitidaException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("SALDO_LIMITE_EXCEDIDO"));
        assertThat(produto.getSaldo()).isEqualTo(Integer.MAX_VALUE);
        verifyNoInteractions(movimentacoes);
    }

    private void referenciasValidas() {
        when(usuarios.findById(2L)).thenReturn(Optional.of(usuario));
        when(produtos.buscarParaAtualizarEstoque(1L)).thenReturn(Optional.of(produto));
    }
}
