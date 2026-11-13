package br.com.sglu.service;

import br.com.sglu.exception.OperacaoNaoPermitidaException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PedidoCadastroServiceTest {
    final PedidoService pedidos = mock(PedidoService.class);
    final PedidoCadastroService cadastro = new PedidoCadastroService(pedidos);

    @Test
    void repeteAposColisaoConfirmadaPeloBanco() {
        when(pedidos.criar(null, 1L)).thenThrow(colisao()).thenReturn(null);
        assertThat(cadastro.criar(null, 1L)).isNull();
        verify(pedidos, times(2)).criar(null, 1L);
    }

    @Test
    void limitaTentativasSemRepeticaoInfinita() {
        when(pedidos.criar(null, 1L)).thenThrow(colisao());
        assertThatThrownBy(() -> cadastro.criar(null, 1L)).isInstanceOfSatisfying(OperacaoNaoPermitidaException.class,
                erro -> assertThat(erro.getCodigo()).isEqualTo("CODIGO_CONSULTA_INDISPONIVEL"));
        verify(pedidos, times(5)).criar(null, 1L);
    }

    @Test
    void naoRepeteViolacoesDeOutrasRestricoes() {
        var erro = new DataIntegrityViolationException("outra restrição");
        when(pedidos.criar(null, 1L)).thenThrow(erro);
        assertThatThrownBy(() -> cadastro.criar(null, 1L)).isSameAs(erro);
        verify(pedidos).criar(null, 1L);
    }

    private DataIntegrityViolationException colisao() {
        return new DataIntegrityViolationException("código duplicado",
                new ConstraintViolationException("duplicado", new SQLException("duplicado", "23505"), "uk_pedidos_codigo_consulta"));
    }
}
