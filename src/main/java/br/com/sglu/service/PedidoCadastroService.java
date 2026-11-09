package br.com.sglu.service;

import br.com.sglu.dto.pedido.PedidoRequest;
import br.com.sglu.dto.pedido.PedidoResponse;
import br.com.sglu.exception.OperacaoNaoPermitidaException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Coordena novas tentativas fora da transação que falhou; cada chamada ao serviço abre outra transação. */
@Service
public class PedidoCadastroService {
    private static final int MAX_TENTATIVAS = 5;
    private final PedidoService pedidos;

    public PedidoCadastroService(PedidoService pedidos) { this.pedidos = pedidos; }

    public PedidoResponse criar(PedidoRequest dados, Long usuarioId) {
        for (int tentativa = 0; tentativa < MAX_TENTATIVAS; tentativa++) {
            try {
                return pedidos.criar(dados, usuarioId);
            } catch (DataIntegrityViolationException exception) {
                if (!colisaoCodigo(exception)) throw exception;
            }
        }
        throw new OperacaoNaoPermitidaException("CODIGO_CONSULTA_INDISPONIVEL",
                "Não foi possível reservar um código de consulta. Tente novamente");
    }

    private static boolean colisaoCodigo(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                // H2 informa o índice da UNIQUE; PostgreSQL informa o nome da restrição.
                return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("uk_pedidos_codigo_consulta");
            }
        }
        return false;
    }
}
