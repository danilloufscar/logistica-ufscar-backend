package br.com.sglu;

import br.com.sglu.support.BancoDeTeste;
import br.com.sglu.dto.estoque.AjusteEstoqueRequest;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Produto;
import br.com.sglu.model.TipoMovimentacaoEstoque;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.MovimentacaoEstoqueRepository;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstoqueAjusteIntegrationTest {

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
    MovimentacaoEstoqueRepository movimentacoes;
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

        produto = new Produto("PROD-001", "Produto Teste", 5);
        produto.ajustarSaldo(10);
        produto = produtos.save(produto);
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

        return json.readTree(response).get("token").asString();
    }

    @Test
    void administradorDeveRealizarAjusteDeInventarioParaCima() throws Exception {
        var request = new AjusteEstoqueRequest(produto.getId(), 15, "Contagem física anual");

        mvc.perform(post("/api/estoque/ajustes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produtoId").value(produto.getId()))
                .andExpect(jsonPath("$.saldoAnterior").value(10))
                .andExpect(jsonPath("$.saldoAtual").value(15))
                .andExpect(jsonPath("$.quantidadeAjustada").value(5))
                .andExpect(jsonPath("$.tipo").value("AJUSTE_ENTRADA"))
                .andExpect(jsonPath("$.justificativa").value("Contagem física anual"));

        var produtoAtualizado = produtos.findById(produto.getId()).orElseThrow();
        assertThat(produtoAtualizado.getSaldo()).isEqualTo(15);

        var list = movimentacoes.findAll();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTipo()).isEqualTo(TipoMovimentacaoEstoque.AJUSTE_ENTRADA);
        assertThat(list.get(0).getJustificativa()).isEqualTo("Contagem física anual");
    }

    @Test
    void administradorDeveRealizarAjusteDeInventarioParaBaixo() throws Exception {
        var request = new AjusteEstoqueRequest(produto.getId(), 4, "Avaria na contagem");

        mvc.perform(post("/api/estoque/ajustes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoAnterior").value(10))
                .andExpect(jsonPath("$.saldoAtual").value(4))
                .andExpect(jsonPath("$.quantidadeAjustada").value(6))
                .andExpect(jsonPath("$.tipo").value("AJUSTE_SAIDA"));

        var produtoAtualizado = produtos.findById(produto.getId()).orElseThrow();
        assertThat(produtoAtualizado.getSaldo()).isEqualTo(4);
    }

    @Test
    void operadorDeveReceberAcessoNegadoAoTentarAjustar() throws Exception {
        var request = new AjusteEstoqueRequest(produto.getId(), 12, "Tentativa Operador");

        mvc.perform(post("/api/estoque/ajustes")
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRetornarErro400QuandoSaldoContadoForIgualAoAtual() throws Exception {
        var request = new AjusteEstoqueRequest(produto.getId(), 10, "Ajuste sem alteração");

        mvc.perform(post("/api/estoque/ajustes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornarErro400QuandoJustificativaForVazia() throws Exception {
        var request = new AjusteEstoqueRequest(produto.getId(), 12, "   ");

        mvc.perform(post("/api/estoque/ajustes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}