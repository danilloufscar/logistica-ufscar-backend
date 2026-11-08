package br.com.sglu.dto.pedido;

import br.com.sglu.model.StatusPedido;
import jakarta.validation.constraints.NotNull;


public record AvancoStatusRequest(@NotNull(message = "Status atual é obrigatório") StatusPedido statusAtual) {
}