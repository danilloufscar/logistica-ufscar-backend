package br.com.sglu;

import br.com.sglu.dto.estoque.AjusteEstoqueRequest;
import br.com.sglu.dto.estoque.EntradaEstoqueRequest;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Produto;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
import br.com.sglu.support.BancoDeTeste;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstoqueMovimentacoesIntegrationTest {

    private static final String PASSWORD = "senha-ficticia-123";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    ProdutoRepository produtos;
    @Autowired
    PasswordEncoder passwords;
    @Autowired
    JdbcTemplate jdbc;

    String adminToken;
    String operadorToken;
    Produto produto;

    @BeforeEach
    void setUp() throws Exception {
        BancoDeTeste.limpar(jdbc);

        String hash = passwords.encode(PASSWORD);
        usuarios.save(new Usuario("Administrador", "admin@example.com", hash, Perfil.ADMINISTRADOR));
        usuarios.save(new Usuario("Operador", "operador@example.com", hash, Perfil.OPERADOR));

        adminToken = login("admin@example.com");
        operadorToken = login("operador@example.com");

        produto = produtos.save(new Produto("PROD-001", "Produto Teste", 5));
    }

    private String login(String email) throws Exception {
        var body = Map.of("email", email, "senha", PASSWORD);
        var response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(response).get("token").asText();
    }

    @Test
    void deveListarEntradasEAjustesDoProdutoOrdenadosDoMaisRecenteParaOMaisAntigo() throws Exception {
        // 1. Registra entrada de 20 unidades
        var entrada = new EntradaEstoqueRequest(produto.getId(), 20, LocalDate.of(2026, 11, 10));
        mvc.perform(post("/api/estoque/entradas")
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(entrada)))
                .andExpect(status().isCreated());

        // 2. Registra ajuste de inventário para 17 unidades (-3)
        var ajuste = new AjusteEstoqueRequest(produto.getId(), 17, "Perda na armazenagem");
        mvc.perform(post("/api/estoque/ajustes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ajuste)))
                .andExpect(status().isCreated());

        // 3. Consulta movimentações como Operador
        mvc.perform(get("/api/estoque/{produtoId}/movimentacoes", produto.getId())
                .header("Authorization", "Bearer " + operadorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("AJUSTE_SAIDA"))
                .andExpect(jsonPath("$[0].quantidade").value(3))
                .andExpect(jsonPath("$[0].justificativa").value("Perda na armazenagem"))
                .andExpect(jsonPath("$[0].saldoApos").value(17))
                .andExpect(jsonPath("$[1].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$[1].quantidade").value(20))
                .andExpect(jsonPath("$[1].saldoApos").value(20));
    }

    @Test
    void deveListarAsUltimasEntradasPelaDataInformadaSemIncluirAjustes() throws Exception {
        registrarEntrada(5, LocalDate.of(2026, 11, 1));
        registrarEntrada(7, LocalDate.of(2026, 11, 3));
        registrarEntrada(9, LocalDate.of(2026, 11, 2));
        mvc.perform(post("/api/estoque/ajustes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AjusteEstoqueRequest(produto.getId(), 20, "Contagem mensal"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/estoque/entradas").param("limite", "2")
                .header("Authorization", "Bearer " + operadorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$[0].quantidade").value(7))
                .andExpect(jsonPath("$[0].saldoApos").value(12))
                .andExpect(jsonPath("$[0].produtoNome").value(produto.getNome()))
                .andExpect(jsonPath("$[1].quantidade").value(9))
                .andExpect(jsonPath("$[1].saldoApos").value(21));
    }

    private void registrarEntrada(int quantidade, LocalDate data) throws Exception {
        mvc.perform(post("/api/estoque/entradas")
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new EntradaEstoqueRequest(produto.getId(), quantidade, data))))
                .andExpect(status().isCreated());
    }

    @Test
    void deveRetornarListaVaziaQuandoProdutoNaoPossuiMovimentacoes() throws Exception {
        mvc.perform(get("/api/estoque/{produtoId}/movimentacoes", produto.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deveRetornarErroQuandoProdutoNaoExistir() throws Exception {
        mvc.perform(get("/api/estoque/{produtoId}/movimentacoes", 99999L)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }
}