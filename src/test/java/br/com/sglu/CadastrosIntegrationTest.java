package br.com.sglu;

import br.com.sglu.model.Cliente;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Produto;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.ClienteRepository;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CadastrosIntegrationTest {
    private static final String PASSWORD = "senha-ficticia-123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @MockitoSpyBean ProdutoRepository produtos;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        produtos.deleteAll();
        clientes.deleteAll();
        usuarios.deleteAll();
        String hash = passwords.encode(PASSWORD);
        usuarios.save(new Usuario("Administrador", "admin@example.com", hash, Perfil.ADMINISTRADOR));
        usuarios.save(new Usuario("Operador", "operador@example.com", hash, Perfil.OPERADOR));
        adminToken = login("admin@example.com");
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void ambosPerfisCadastramEListamClientesEProdutos(Perfil perfil) throws Exception {
        String token = perfil == Perfil.ADMINISTRADOR ? adminToken : login("operador@example.com");
        cadastrar("clientes", clienteValido(), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.nome").value("Cliente Teste"))
                .andExpect(jsonPath("$.telefone").value("+55 (16) 99999-0000"))
                .andExpect(jsonPath("$.endereco").value("Rua Fictícia, 10"));
        cadastrar("produtos", produtoValido(), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.codigo").value("PROD-001"))
                .andExpect(jsonPath("$.nome").value("Produto Teste")).andExpect(jsonPath("$.estoqueMinimo").value(5));
        mvc.perform(get("/api/clientes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Cliente Teste"));
        mvc.perform(get("/api/produtos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("PROD-001"));
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(produtos.count()).isEqualTo(1);
    }

    @Test
    void consultasVaziasRetornamArrayVazio() throws Exception {
        for (String recurso : List.of("clientes", "produtos")) {
            mvc.perform(get("/api/" + recurso).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Test
    void exigeAutenticacaoNasQuatroOperacoes() throws Exception {
        for (String recurso : List.of("clientes", "produtos")) {
            mvc.perform(get("/api/" + recurso)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/" + recurso).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(recurso.equals("clientes") ? clienteValido() : produtoValido())))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(clientes.count()).isZero();
        assertThat(produtos.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("camposInvalidos")
    void rejeitaCamposInvalidosSemPersistir(String recurso, String campo, Object valor, String codigoErro) throws Exception {
        var request = recurso.equals("clientes") ? clienteValido() : produtoValido();
        request.put(campo, valor);
        var result = cadastrar(recurso, request, adminToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(codigoErro));
        if (codigoErro.equals("DADOS_INVALIDOS")) result.andExpect(jsonPath("$.detalhes." + campo).isString());
        assertThat(clientes.count()).isZero();
        assertThat(produtos.count()).isZero();
    }

    static Stream<Arguments> camposInvalidos() {
        return Stream.of(
                Arguments.of("clientes", "nome", null, "DADOS_INVALIDOS"),
                Arguments.of("clientes", "nome", "  ", "DADOS_INVALIDOS"),
                Arguments.of("clientes", "nome", "a".repeat(121), "DADOS_INVALIDOS"),
                Arguments.of("clientes", "telefone", null, "DADOS_INVALIDOS"),
                Arguments.of("clientes", "telefone", "  ", "DADOS_INVALIDOS"),
                Arguments.of("clientes", "telefone", "1".repeat(31), "DADOS_INVALIDOS"),
                Arguments.of("clientes", "endereco", null, "DADOS_INVALIDOS"),
                Arguments.of("clientes", "endereco", " \t ", "DADOS_INVALIDOS"),
                Arguments.of("clientes", "endereco", "a".repeat(501), "DADOS_INVALIDOS"),
                Arguments.of("produtos", "codigo", null, "DADOS_INVALIDOS"),
                Arguments.of("produtos", "codigo", "  ", "DADOS_INVALIDOS"),
                Arguments.of("produtos", "codigo", "a".repeat(65), "DADOS_INVALIDOS"),
                Arguments.of("produtos", "nome", null, "DADOS_INVALIDOS"),
                Arguments.of("produtos", "nome", "  ", "DADOS_INVALIDOS"),
                Arguments.of("produtos", "nome", "a".repeat(121), "DADOS_INVALIDOS"),
                Arguments.of("produtos", "estoqueMinimo", null, "DADOS_INVALIDOS"),
                Arguments.of("produtos", "estoqueMinimo", -1, "DADOS_INVALIDOS"),
                Arguments.of("produtos", "estoqueMinimo", 1.5, "JSON_INVALIDO"),
                Arguments.of("produtos", "estoqueMinimo", 2.0, "JSON_INVALIDO"),
                Arguments.of("produtos", "estoqueMinimo", 2147483648L, "JSON_INVALIDO"),
                Arguments.of("produtos", "estoqueMinimo", true, "JSON_INVALIDO"));
    }

    @Test
    void aceitaLimitesDosCamposEEstoqueMinimoZero() throws Exception {
        var cliente = clienteValido();
        cliente.put("nome", "a".repeat(120)); cliente.put("telefone", "1".repeat(30)); cliente.put("endereco", "a".repeat(500));
        cadastrar("clientes", cliente, adminToken).andExpect(status().isCreated());
        var produto = produtoValido();
        produto.put("codigo", "p".repeat(64)); produto.put("nome", "a".repeat(120)); produto.put("estoqueMinimo", 0);
        cadastrar("produtos", produto, adminToken).andExpect(status().isCreated()).andExpect(jsonPath("$.estoqueMinimo").value(0));
    }

    @Test
    void rejeitaCodigoRepetidoIgnorandoCaixaEEspacos() throws Exception {
        cadastrar("produtos", produtoValido(), adminToken).andExpect(status().isCreated());
        var repetido = produtoValido(); repetido.put("codigo", "  PrOd-001  ");
        cadastrar("produtos", repetido, adminToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CODIGO_PRODUTO_JA_CADASTRADO"));
        assertThat(produtos.count()).isEqualTo(1);
    }

    @Test
    void bancoGaranteUnicidadeMesmoSeChecagemPreviaNaoDetectarDuplicidade() throws Exception {
        cadastrar("produtos", produtoValido(), adminToken).andExpect(status().isCreated());
        doReturn(false).when(produtos).existsByCodigo("PROD-001");
        String response = cadastrar("produtos", produtoValido(), adminToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLITO_DADOS")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("insert into", "SQLException", "uk_produtos_codigo");
        assertThat(produtos.count()).isEqualTo(1);
    }

    @Test
    void bancoTambemImpedeEstoqueMinimoNegativo() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO produtos (codigo, nome, estoque_minimo) VALUES (?, ?, ?)", "INVALIDO", "Produto", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(produtos.count()).isZero();
    }

    @Test
    void listasOrdenadasPorNomeEIdPermitemNomesRepetidos() throws Exception {
        clientes.saveAndFlush(new Cliente("Z", "123", "Rua Z"));
        var primeiro = clientes.saveAndFlush(new Cliente("A", "123", "Rua A"));
        var segundo = clientes.saveAndFlush(new Cliente("A", "123", "Rua A"));
        produtos.saveAndFlush(new Produto("Z", "Z", 1));
        produtos.saveAndFlush(new Produto("A1", "A", 1));
        produtos.saveAndFlush(new Produto("A2", "A", 1));
        mvc.perform(get("/api/clientes").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(primeiro.getId()))
                .andExpect(jsonPath("$[1].id").value(segundo.getId())).andExpect(jsonPath("$[2].nome").value("Z"));
        mvc.perform(get("/api/produtos").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].codigo").value("A1"))
                .andExpect(jsonPath("$[1].codigo").value("A2")).andExpect(jsonPath("$[2].nome").value("Z"));
    }

    @Test
    void rejeitaCorpoAusenteCamposAusentesEJsonMalformado() throws Exception {
        for (String recurso : List.of("clientes", "produtos")) {
            for (String body : List.of("", "{}", "{")) {
                mvc.perform(post("/api/" + recurso).header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
            }
        }
        assertThat(clientes.count()).isZero();
        assertThat(produtos.count()).isZero();
    }

    private Map<String, Object> clienteValido() {
        return new LinkedHashMap<>(Map.of("nome", " Cliente Teste ", "telefone", " +55 (16) 99999-0000 ", "endereco", " Rua Fictícia, 10 "));
    }

    private Map<String, Object> produtoValido() {
        return new LinkedHashMap<>(Map.of("codigo", " prod-001 ", "nome", " Produto Teste ", "estoqueMinimo", 5));
    }

    private ResultActions cadastrar(String recurso, Object body, String token) throws Exception {
        return mvc.perform(post("/api/" + recurso).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private String login(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }
}
