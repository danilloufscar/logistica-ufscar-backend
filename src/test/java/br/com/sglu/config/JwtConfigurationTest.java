package br.com.sglu.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import static org.assertj.core.api.Assertions.*;

class JwtConfigurationTest {
    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    void rejeitaChaveCurtaOuBase64InvalidoSemExporSegredo() {
        for (String secret : new String[]{"não-é-base64!", Base64.getEncoder().encodeToString(new byte[8])}) {
            assertThatThrownBy(() -> configuration.jwtSecretKey(properties(secret)))
                    .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(secret);
        }
    }

    @Test
    void rejeitaTokenAssinadoSemExpiracao() {
        var properties = properties(Base64.getEncoder().encodeToString(new byte[32]));
        var key = configuration.jwtSecretKey(properties);
        var encoder = configuration.jwtEncoder(key);
        var decoder = configuration.jwtDecoder(key, properties, Clock.systemUTC());
        var claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .subject("teste@example.com").issuedAt(Instant.now()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    private JwtProperties properties(String secret) {
        return new JwtProperties(secret, "sglu-backend", Duration.ofHours(1));
    }
}
