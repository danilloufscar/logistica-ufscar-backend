package br.com.sglu;

import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.UsuarioRepository;
import br.com.sglu.service.AdministradorInicialService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthIntegrationTest.ProtectedEndpoints.class)
class AuthIntegrationTest {
    private static final String PASSWORD = "senha-teste-123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired AdministradorInicialService bootstrap;
    Usuario admin;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        bootstrap.inicializar("Administrador", "admin@example.com", PASSWORD);
        admin = users.findByEmail("admin@example.com").orElseThrow();
        users.save(new Usuario("Operador", "operador@example.com", passwords.encode(PASSWORD), Perfil.OPERADOR));
    }

    @Test
    void loginRetornaJwtComContratoDoFrontendESemSenha() throws Exception {
        String token = login(" ADMIN@example.com ", PASSWORD);
        var jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("admin@example.com");
        assertThat(jwt.getClaimAsString("nome")).isEqualTo("Administrador");
        assertThat(jwt.getClaimAsString("perfil")).isEqualTo("ADMINISTRADOR");
        assertThat(((Number) jwt.getClaims().get("id")).longValue()).isEqualTo(admin.getId());
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(3600));
        assertThat(jwt.getClaims()).doesNotContainKeys("senha", "senhaHash", "password");
        assertThat(admin.getSenhaHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, admin.getSenhaHash())).isTrue();
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void operadorPodeAcessarRotaInternaMasNaoAdministrativa() throws Exception {
        String token = login("operador@example.com", PASSWORD);
        assertThat(decoder.decode(token).getClaimAsString("perfil")).isEqualTo("OPERADOR");
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    void senhaIncorretaEEmailDesconhecidoTemMesmaResposta() throws Exception {
        String wrongPassword = deniedLogin("admin@example.com", "incorreta");
        String unknownUser = deniedLogin("desconhecido@example.com", PASSWORD);
        assertThat(unknownUser).isEqualTo(wrongPassword);
        assertThat(wrongPassword).doesNotContain(PASSWORD, admin.getSenhaHash());
    }

    @Test
    void rejeitaCamposInvalidosEJsonInvalido() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "invalido", "senha", ""))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaTokenAusenteInvalidoExpiradoEDeOutroEmissor() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        for (String token : new String[]{"token-invalido",
                signedToken("sglu-backend", Instant.now().minusSeconds(60)),
                signedToken("outro-emissor", Instant.now().plusSeconds(3600))}) {
            mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rejeitaAssinaturaAdulterada() throws Exception {
        String token = login("admin@example.com", PASSWORD);
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reinicializarNaoTrocaCredenciaisDoAdministrador() {
        bootstrap.inicializar("Outro", "outro@example.com", "outra-senha-123");
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findById(admin.getId()).orElseThrow().getSenhaHash()).isEqualTo(admin.getSenhaHash());
    }

    private String login(String email, String senha) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", senha))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.senha").doesNotExist()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String deniedLogin(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", senha))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha incorretos"))
                .andExpect(jsonPath("$.token").doesNotExist()).andReturn().getResponse().getContentAsString();
    }

    private String signedToken(String issuer, Instant expiration) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("admin@example.com")
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(expiration)
                .claim("perfil", "ADMINISTRADOR").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @RestController
    static class ProtectedEndpoints {
        @GetMapping("/api/pedidos")
        Map<String, String> resource() { return Map.of("status", "ok"); }
    }
}
