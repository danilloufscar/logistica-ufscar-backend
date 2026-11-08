package br.com.sglu.dto.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroClienteRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres") String nome,
        @NotBlank(message = "Telefone é obrigatório")
        @Size(max = 30, message = "Telefone deve ter no máximo 30 caracteres") String telefone,
        @NotBlank(message = "Endereço é obrigatório")
        @Size(max = 500, message = "Endereço deve ter no máximo 500 caracteres") String endereco) {

    public CadastroClienteRequest {
        if (nome != null) nome = nome.strip();
        if (telefone != null) telefone = telefone.strip();
        if (endereco != null) endereco = endereco.strip();
    }
}
