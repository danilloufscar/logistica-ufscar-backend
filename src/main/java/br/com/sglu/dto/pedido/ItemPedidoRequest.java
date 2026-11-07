package br.com.sglu.dto.pedido;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemPedidoRequest(
        @NotNull(message = "Produto é obrigatório") Long produtoId,
        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser maior que zero") Integer quantidade) {
}

