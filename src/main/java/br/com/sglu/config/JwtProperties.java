package br.com.sglu.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sglu.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration expiration) {

    @AssertTrue(message = "A duração do token deve ser positiva")
    public boolean isExpirationPositive() {
        return expiration != null && expiration.compareTo(Duration.ZERO) > 0;
    }

    @Override
    public String toString() {
        return "JwtProperties[secret omitido, issuer=" + issuer + ", expiration=" + expiration + "]";
    }
}
