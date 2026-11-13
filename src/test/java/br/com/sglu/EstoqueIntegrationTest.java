package br.com.sglu;

import br.com.sglu.dto.estoque.EntradaEstoqueRequest;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Produto;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
import br.com.sglu.service.EstoqueService;
import br.com.sglu.support.BancoDeTeste;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstoqueIntegrationTest {
    private static final String SENHA = "senha-ficticia-123";
    private static final LocalDate DATA = LocalDate.of(2026, 11, 9);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProdutoRepository produtos;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired EstoqueService estoque;
    Long produtoId;
    Long adminId;
    String token;

    @BeforeEach
    void preparar() throws Exception {
        BancoDeTeste.limpar(jdbc);
        String hash = encoder.encode(SENHA);
        adminId = usuarios.saveAndFlush(new Usuario("Administrador", "admin@example.com", hash, Perfil.ADMINISTRADOR)).getId();
        usuarios.saveAndFlush(new Usuario("Operador", "operador@example.com", hash, Perfil.OPERADOR));
        produtoId = produtos.saveAndFlush(new Produto("P-001", "Produto de teste", 5)).getId();
        token = login("admin@example.com");
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void ambosPerfisRegistramEntradasSomandoSaldoERegistrandoAutoria(Perfil perfil) throws Exception {
        String email = perfil == Perfil.ADMINISTRADOR ? "admin@example.com" : "operador@example.com";
        String jwt = login(email);
        assertThat(saldo()).isZero();
        enviar(dados(10), jwt).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.quantidade").value(10))
                .andExpect(jsonPath("$.produto.codigo").value("P-001"))
                .andExpect(jsonPath("$.data").value(DATA.toString()))
                .andExpect(jsonPath("$.registradoEm").isString())
                .andExpect(jsonPath("$.saldoAtual").value(10))
                .andExpect(jsonPath("$.usuario.id").value(usuarios.findByEmail(email).orElseThrow().getId()));
        enviar(dados(5), jwt).andExpect(status().isCreated()).andExpect(jsonPath("$.saldoAtual").value(15));
        assertThat(saldo()).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(quantidade) FROM movimentacoes_estoque", Integer.class)).isEqualTo(15);
    }

    @ParameterizedTest
    @MethodSource("invalidos")
    void rejeitaCamposInvalidosSemAlterarSaldoOuMovimentacoes(String campo, Object valor, String codigo) throws Exception {
        var entrada = dados(10);
        entrada.put(campo, valor);
        enviar(entrada, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value(codigo));
        semEntrada();
    }

    static Stream<Arguments> invalidos() {
        return Stream.of(
                Arguments.of("produtoId", null, "DADOS_INVALIDOS"),
                Arguments.of("produtoId", 0, "DADOS_INVALIDOS"),
                Arguments.of("produtoId", -1, "DADOS_INVALIDOS"),
                Arguments.of("produtoId", 999999, "DADOS_INVALIDOS"),
                Arguments.of("quantidade", null, "DADOS_INVALIDOS"),
                Arguments.of("quantidade", 0, "DADOS_INVALIDOS"),
                Arguments.of("quantidade", -1, "DADOS_INVALIDOS"),
                Arguments.of("quantidade", 1.5, "JSON_INVALIDO"),
                Arguments.of("quantidade", 2.0, "JSON_INVALIDO"),
                Arguments.of("quantidade", true, "JSON_INVALIDO"),
                Arguments.of("quantidade", 2147483648L, "JSON_INVALIDO"),
                Arguments.of("data", null, "DADOS_INVALIDOS"),
                Arguments.of("data", "2026-02-30", "JSON_INVALIDO"),
                Arguments.of("data", "09/11/2026", "JSON_INVALIDO"));
    }

    @Test
    void exigeTokenEUsuarioExistente() throws Exception {
        enviar(dados(1), null).andExpect(status().isUnauthorized());
        enviar(dados(1), "invalido").andExpect(status().isUnauthorized());
        usuarios.deleteById(adminId);
        enviar(dados(1), token).andExpect(status().isUnauthorized());
        semEntrada();
    }

    @Test
    void camposAusentesEJsonMalformadoNaoGeramEntrada() throws Exception {
        enviar(Map.of(), token).andExpect(status().isBadRequest());
        mvc.perform(post("/api/estoque/entradas").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
        semEntrada();
    }

    @Test
    void limiteDoSaldoNaoCausaOverflowNemMovimentacaoParcial() throws Exception {
        enviar(dados(Integer.MAX_VALUE), token).andExpect(status().isCreated());
        enviar(dados(1), token).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_LIMITE_EXCEDIDO"));
        assertThat(saldo()).isEqualTo(Integer.MAX_VALUE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque", Integer.class)).isEqualTo(1);
    }

    @Test
    void falhaRealNaGravacaoDaMovimentacaoDesfazAlteracaoDoSaldo() {
        jdbc.execute("ALTER TABLE movimentacoes_estoque ADD CONSTRAINT teste_recusa_sete CHECK (quantidade <> 7)");
        try {
            assertThatThrownBy(() -> estoque.registrarEntrada(new EntradaEstoqueRequest(produtoId, 7, DATA), adminId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            semEntrada();
        } finally {
            jdbc.execute("ALTER TABLE movimentacoes_estoque DROP CONSTRAINT teste_recusa_sete");
        }
    }

    @Test
    void entradasSimultaneasNaoPerdemSaldo() throws Exception {
        var inicio = new CyclicBarrier(4);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tarefas = List.of(10, 5, 7, 3).stream().map(quantidade -> executor.submit(() -> {
                inicio.await(10, TimeUnit.SECONDS);
                return estoque.registrarEntrada(new EntradaEstoqueRequest(produtoId, quantidade, DATA), adminId);
            })).toList();
            for (var tarefa : tarefas) assertThat(tarefa.get(30, TimeUnit.SECONDS).tipo().name()).isEqualTo("ENTRADA");
        }
        assertThat(saldo()).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT SUM(quantidade) FROM movimentacoes_estoque", Integer.class)).isEqualTo(25);
    }

    @Test
    void bancoProtegeSaldoEQuantidadeEAutorDaMovimentacao() throws Exception {
        assertThatThrownBy(() -> jdbc.update("UPDATE produtos SET saldo=-1 WHERE id=?", produtoId))
                .isInstanceOf(DataIntegrityViolationException.class);
        enviar(dados(1), token).andExpect(status().isCreated());
        assertThatThrownBy(() -> jdbc.update("UPDATE movimentacoes_estoque SET quantidade=0"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM usuarios WHERE id=?", adminId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(saldo()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void consultaSaldoAtualEAlertaNosLimitesParaAmbosPerfis(Perfil perfil) throws Exception {
        String jwt = login(perfil == Perfil.ADMINISTRADOR ? "admin@example.com" : "operador@example.com");
        produtos.saveAndFlush(new Produto("ZERO", "Zero unidades", 0));
        consultar(jwt).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(produtoId))
                .andExpect(jsonPath("$[0].codigo").value("P-001"))
                .andExpect(jsonPath("$[0].nome").value("Produto de teste"))
                .andExpect(jsonPath("$[0].saldo").value(0))
                .andExpect(jsonPath("$[0].estoqueMinimo").value(5))
                .andExpect(jsonPath("$[0].abaixoOuNoMinimo").value(true))
                .andExpect(jsonPath("$[1].saldo").value(0))
                .andExpect(jsonPath("$[1].estoqueMinimo").value(0))
                .andExpect(jsonPath("$[1].abaixoOuNoMinimo").value(true));

        enviar(dados(5), jwt).andExpect(status().isCreated());
        consultar(jwt).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].saldo").value(5))
                .andExpect(jsonPath("$[0].abaixoOuNoMinimo").value(true));
        enviar(dados(1), jwt).andExpect(status().isCreated());
        consultar(jwt).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].saldo").value(6))
                .andExpect(jsonPath("$[0].abaixoOuNoMinimo").value(false));
        // Consultas não alteram o saldo nem geram novas movimentações.
        assertThat(saldo()).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque", Integer.class)).isEqualTo(2);
    }

    @Test
    void listaTodosProdutosMesmoSemMovimentacaoEmOrdemEstavel() throws Exception {
        Long primeiro = produtos.saveAndFlush(new Produto("A1", "Alfa", 1)).getId();
        Long segundo = produtos.saveAndFlush(new Produto("A2", "Alfa", 2)).getId();
        consultar(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(primeiro))
                .andExpect(jsonPath("$[1].id").value(segundo))
                .andExpect(jsonPath("$[2].id").value(produtoId));
        semEntrada();
    }

    @Test
    void consultaSemProdutosRetornaListaVazia() throws Exception {
        produtos.deleteAll();
        consultar(token).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void consultaEstoqueExigeAutenticacao() throws Exception {
        mvc.perform(get("/api/estoque")).andExpect(status().isUnauthorized());
        consultar("invalido").andExpect(status().isUnauthorized());
        semEntrada();
    }

    private ResultActions consultar(String jwt) throws Exception {
        return mvc.perform(get("/api/estoque").header("Authorization", "Bearer " + jwt));
    }

    private Map<String, Object> dados(int quantidade) {
        return new LinkedHashMap<>(Map.of("produtoId", produtoId, "quantidade", quantidade, "data", DATA.toString()));
    }

    private int saldo() { return produtos.findById(produtoId).orElseThrow().getSaldo(); }

    private void semEntrada() {
        assertThat(saldo()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque", Integer.class)).isZero();
    }

    private ResultActions enviar(Object dados, String jwt) throws Exception {
        var request = post("/api/estoque/entradas").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dados));
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return mvc.perform(request);
    }

    private String login(String email) throws Exception {
        var resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "senha", SENHA))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("token").asText();
    }
}
