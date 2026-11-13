package br.com.sglu.dto.pedido;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OcorrenciaPedidoRequest(
        @NotBlank(message = "A descrição da ocorrência é obrigatória") @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres") String descricao) {
}