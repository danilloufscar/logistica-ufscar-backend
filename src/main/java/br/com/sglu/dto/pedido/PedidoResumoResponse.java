package br.com.sglu.dto.pedido;

import br.com.sglu.model.Pedido;
import br.com.sglu.model.StatusPedido;
import java.time.LocalDate;

public record PedidoResumoResponse(Long id, String codigo, PedidoResponse.ClienteResumo cliente, StatusPedido status,
                                   LocalDate prazoEntrega, boolean atrasado) {

    public static PedidoResumoResponse from(Pedido pedido, LocalDate hoje) {
        return new PedidoResumoResponse(pedido.getId(), pedido.getCodigoConsulta(),
                PedidoResponse.ClienteResumo.from(pedido.getCliente()), pedido.getStatus(), pedido.getPrazoEntrega(),
                pedido.isAtrasado(hoje));
    }
}
