package br.com.sglu.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sglu.bootstrap.admin")
public record BootstrapAdminProperties(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String senha) {

    @AssertTrue(message = "A senha inicial deve ter no máximo 72 bytes em UTF-8")
    public boolean isSenhaCompativelComBCrypt() {
        return senha != null && senha.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[credenciais omitidas]";
    }
}
