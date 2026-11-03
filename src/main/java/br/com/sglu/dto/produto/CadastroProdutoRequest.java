package br.com.sglu.dto.produto;

import br.com.sglu.model.Produto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CadastroProdutoRequest(
        @NotBlank(message = "Código é obrigatório")
        @Size(max = 64, message = "Código deve ter no máximo 64 caracteres") String codigo,
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres") String nome,
        @NotNull(message = "Estoque mínimo é obrigatório")
        @PositiveOrZero(message = "Estoque mínimo deve ser maior ou igual a zero") Integer estoqueMinimo) {

    public CadastroProdutoRequest {
        if (codigo != null) codigo = Produto.normalizarCodigo(codigo);
        if (nome != null) nome = nome.strip();
    }
}
