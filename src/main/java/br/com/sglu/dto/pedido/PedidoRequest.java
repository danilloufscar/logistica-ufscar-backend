package br.com.sglu.dto.pedido;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record PedidoRequest(
        @NotNull(message = "Cliente é obrigatório") Long clienteId,
        @NotBlank(message = "Endereço de entrega é obrigatório")
        @Size(max = 500, message = "Endereço de entrega deve ter no máximo 500 caracteres") String enderecoEntrega,
        @NotNull(message = "Prazo de entrega é obrigatório") LocalDate prazoEntrega,
        @NotEmpty(message = "Informe ao menos um item")
        @Size(max = 100, message = "O pedido deve ter no máximo 100 itens")
        List<@NotNull(message = "Item inválido") @Valid ItemPedidoRequest> itens) {

    public PedidoRequest {
        if (enderecoEntrega != null) enderecoEntrega = enderecoEntrega.strip();
    }
}