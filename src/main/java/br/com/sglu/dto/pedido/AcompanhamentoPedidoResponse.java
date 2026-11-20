package br.com.sglu.dto.pedido;

import br.com.sglu.model.Pedido;
import br.com.sglu.model.StatusPedido;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AcompanhamentoPedidoResponse(
        String codigo,
        String clienteNome,
        String enderecoEntrega,
        LocalDate prazoEntrega,
        StatusPedido status,
        String statusRotulo,
        boolean atrasado,
        Instant criadoEm,
        Instant atualizadoEm,
        int intervaloAtualizacaoSegundos,
        List<Item> itens,
        List<TimelineEventoResponse> timeline) {
    public static final int INTERVALO_POLLING_SEGUNDOS = 30;

    public record Item(String codigoProduto, String nomeProduto, Integer quantidade) {
    }

    public static AcompanhamentoPedidoResponse from(Pedido pedido, LocalDate hoje, Instant agora,
            List<TimelineEventoResponse> timeline) {
        var itens = pedido.getItens().stream()
                .map(i -> new Item(i.getProduto().getCodigo(), i.getProduto().getNome(), i.getQuantidade()))
                .toList();

        return new AcompanhamentoPedidoResponse(
                pedido.getCodigoConsulta(),
                pedido.getCliente().getNome(),
                pedido.getEnderecoEntrega(),
                pedido.getPrazoEntrega(),
                pedido.getStatus(),
                pedido.getStatus().getRotulo(),
                pedido.isAtrasado(hoje),
                pedido.getCriadoEm(),
                agora,
                INTERVALO_POLLING_SEGUNDOS,
                itens,
                timeline);
    }
}