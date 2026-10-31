package br.com.sglu.dto.usuario;

import br.com.sglu.model.Perfil;
import br.com.sglu.validation.SenhaBCrypt;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CadastroUsuarioRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
        String nome,
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres")
        String email,
        @NotBlank(message = "Senha é obrigatória")
        @SenhaBCrypt
        String senha,
        @NotNull(message = "Perfil é obrigatório")
        Perfil perfil) {

    public CadastroUsuarioRequest {
        if (nome != null) nome = nome.strip();
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "CadastroUsuarioRequest[dados omitidos]";
    }
}
