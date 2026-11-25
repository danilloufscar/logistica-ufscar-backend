package br.com.sglu.dto.pedido;

import br.com.sglu.model.HistoricoStatusPedido;
import br.com.sglu.model.Pedido;
import br.com.sglu.model.StatusPedido;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta pública do Portal do Cliente (RF17): somente status, prazo e as mudanças de status.
 * Não expõe itens, cliente, endereço, usuários nem ocorrências internas.
 */
public record AcompanhamentoPedidoResponse(
        String codigo,
        StatusPedido status,
        String statusRotulo,
        LocalDate prazoEntrega,
        Instant atualizadoEm,
        int intervaloAtualizacaoSegundos,
        List<Etapa> timeline) {
    public static final int INTERVALO_POLLING_SEGUNDOS = 10;

    public record Etapa(StatusPedido status, String statusRotulo, Instant dataHora) {
        static Etapa from(HistoricoStatusPedido registro) {
            return new Etapa(registro.getStatus(), registro.getStatus().getRotulo(), registro.getDataHora());
        }
    }

    public static AcompanhamentoPedidoResponse from(Pedido pedido, Instant agora) {
        return new AcompanhamentoPedidoResponse(
                pedido.getCodigoConsulta(),
                pedido.getStatus(),
                pedido.getStatus().getRotulo(),
                pedido.getPrazoEntrega(),
                agora,
                INTERVALO_POLLING_SEGUNDOS,
                pedido.getHistorico().stream().map(Etapa::from).toList());
    }
}
