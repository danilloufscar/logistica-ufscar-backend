package br.com.sglu.dto.estoque;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record EntradaEstoqueRequest(
        @NotNull(message = "Produto é obrigatório")
        @Positive(message = "Produto inválido") Long produtoId,
        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser maior que zero") Integer quantidade,
        @NotNull(message = "Data é obrigatória") LocalDate data) {}
