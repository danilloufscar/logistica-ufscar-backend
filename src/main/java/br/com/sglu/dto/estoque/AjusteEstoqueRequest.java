package br.com.sglu.dto.estoque;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AjusteEstoqueRequest(
        @NotNull(message = "O produto e obrigatorio") Long produtoId,

        @NotNull(message = "A quantidade contada e obrigatoria") @Min(value = 0, message = "A quantidade contada nao pode ser negativa") Integer quantidadeContada,

        @NotBlank(message = "A justificativa e obrigatoria") @Size(max = 500, message = "A justificativa deve ter no maximo 500 caracteres") String justificativa) {
}