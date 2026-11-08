package br.com.sglu;

import br.com.sglu.dto.usuario.CadastroUsuarioRequest;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuarioIntegrationTest {
    private static final String PASSWORD = "senha-ficticia-123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @MockitoSpyBean UsuarioRepository users;
    String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        users.save(new Usuario("Administrador", "admin@example.com", passwords.encode(PASSWORD), Perfil.ADMINISTRADOR));
        users.save(new Usuario("Operador", "operador@example.com", passwords.encode(PASSWORD), Perfil.OPERADOR));
        adminToken = login("admin@example.com", PASSWORD);
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void administradorCadastraAmbosPerfisENovoUsuarioPodeEntrar(Perfil perfil) throws Exception {
        var request = validRequest();
        request.put("nome", "  Novo usuário  ");
        request.put("email", "  NOVO@example.com  ");
        request.put("perfil", perfil.name());
        String response = mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Novo usuário"))
                .andExpect(jsonPath("$.email").value("novo@example.com"))
                .andExpect(jsonPath("$.perfil").value(perfil.name()))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> fields = json.readValue(response, new TypeReference<Map<String, Object>>() {});
        assertThat(fields.keySet()).containsExactlyInAnyOrder("id", "nome", "email", "perfil");
        var saved = users.findByEmail("novo@example.com").orElseThrow();
        assertThat(saved.getSenhaHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, saved.getSenhaHash())).isTrue();
        assertThat(response).doesNotContain(PASSWORD, saved.getSenhaHash());
        String token = login("novo@example.com", PASSWORD);
        assertThat(decoder.decode(token).getClaimAsString("perfil")).isEqualTo(perfil.name());
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().is(perfil == Perfil.ADMINISTRADOR ? 200 : 403));
    }

    @Test
    void administradorListaSemExporSenhaOuHash() throws Exception {
        String response = mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Administrador"))
                .andExpect(jsonPath("$[1].nome").value("Operador"))
                .andExpect(jsonPath("$[*].senha").doesNotExist())
                .andExpect(jsonPath("$[*].senhaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(PASSWORD, "$2");
    }

    @Test
    void operadorNaoPodeListarNemCadastrarMesmoTentandoCriarAdministrador() throws Exception {
        String token = login("operador@example.com", PASSWORD);
        var request = validRequest();
        request.put("perfil", "ADMINISTRADOR");
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void exigeAutenticacaoParaListarECadastrar() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
        assertThat(users.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejeitaCamposInvalidosSemPersistir(String field, Object value) throws Exception {
        var request = validRequest();
        request.put(field, value);
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(2);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("nome", null), Arguments.of("nome", "  "), Arguments.of("nome", "a".repeat(121)),
                Arguments.of("email", null), Arguments.of("email", ""), Arguments.of("email", "email-invalido"),
                Arguments.of("email", "a".repeat(245) + "@example.com"),
                Arguments.of("senha", null), Arguments.of("senha", "  "), Arguments.of("senha", "a".repeat(73)),
                Arguments.of("senha", "á".repeat(37)), Arguments.of("perfil", null),
                Arguments.of("perfil", "CLIENTE"), Arguments.of("perfil", 0), Arguments.of("perfil", 1));
    }

    @Test
    void aceitaLimiteDe72BytesSemAlterarSenha() throws Exception {
        String password = "á".repeat(35) + "  ";
        var request = validRequest();
        request.put("senha", password);
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated());
        assertThat(login("novo@example.com", password)).isNotBlank();
    }

    @Test
    void rejeitaEmailDuplicadoIgnorandoCaixaEEspacos() throws Exception {
        var request = validRequest();
        request.put("email", " ADMIN@example.com ");
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"));
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void restricaoDoBancoProtegeMesmoSeConsultaPreviaNaoDetectarDuplicidade() throws Exception {
        // Simula uma corrida: outro cadastro ficou visível após a checagem prévia.
        doReturn(false).when(users).existsByEmail("admin@example.com");
        var request = validRequest();
        request.put("email", "admin@example.com");
        String response = mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONFLITO_DADOS"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("insert into", "SQLException", PASSWORD, "$2");
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void rejeitaJsonInvalidoECorpoSemCampos() throws Exception {
        for (String body : new String[]{"{", "{}"}) {
            mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void representacaoDoDtoNaoExpoeCredenciais() {
        var request = new CadastroUsuarioRequest("Pessoa", "pessoa@example.com", PASSWORD, Perfil.OPERADOR);
        assertThat(request.toString()).doesNotContain(PASSWORD, "pessoa@example.com");
    }

    private Map<String, Object> validRequest() {
        return new LinkedHashMap<>(Map.of("nome", "Novo usuário", "email", "novo@example.com",
                "senha", PASSWORD, "perfil", "OPERADOR"));
    }

    private String login(String email, String senha) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", senha))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }
}
