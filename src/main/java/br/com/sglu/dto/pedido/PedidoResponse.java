
package br.com.sglu.dto.pedido;

import br.com.sglu.model.Cliente;
import br.com.sglu.model.Pedido;
import br.com.sglu.model.StatusPedido;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PedidoResponse(Long id, String codigo, ClienteResumo cliente, String enderecoEntrega,
                             LocalDate prazoEntrega, StatusPedido status, boolean atrasado, Instant criadoEm,
                             List<Item> itens, List<Historico> historico) {

    public record ClienteResumo(Long id, String nome) {
        static ClienteResumo from(Cliente cliente) {
            return new ClienteResumo(cliente.getId(), cliente.getNome());
        }
    }

    public record ProdutoResumo(Long id, String codigo, String nome) {}

    public record Item(ProdutoResumo produto, Integer quantidade) {}

    public record UsuarioResumo(Long id, String nome) {}

    public record Historico(StatusPedido status, Instant dataHora, UsuarioResumo usuario) {}

    public static PedidoResponse from(Pedido pedido, LocalDate hoje) {
        var itens = pedido.getItens().stream().map(item -> new Item(
                new ProdutoResumo(item.getProduto().getId(), item.getProduto().getCodigo(), item.getProduto().getNome()),
                item.getQuantidade())).toList();
        var historico = pedido.getHistorico().stream().map(registro -> new Historico(registro.getStatus(),
                registro.getDataHora(), new UsuarioResumo(registro.getUsuario().getId(), registro.getUsuario().getNome()))).toList();
        return new PedidoResponse(pedido.getId(), pedido.getCodigoConsulta(), ClienteResumo.from(pedido.getCliente()),
                pedido.getEnderecoEntrega(), pedido.getPrazoEntrega(), pedido.getStatus(), pedido.isAtrasado(hoje),
                pedido.getCriadoEm(), itens, historico);
    }
}
