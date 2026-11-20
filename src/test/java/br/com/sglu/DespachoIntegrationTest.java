package br.com.sglu;

import br.com.sglu.dto.estoque.EntradaEstoqueRequest;
import br.com.sglu.dto.pedido.ItemPedidoRequest;
import br.com.sglu.dto.pedido.PedidoRequest;
import br.com.sglu.exception.EstoqueInsuficienteException;
import br.com.sglu.exception.OperacaoNaoPermitidaException;
import br.com.sglu.model.*;
import br.com.sglu.repository.*;
import br.com.sglu.service.EstoqueService;
import br.com.sglu.service.PedidoService;
import br.com.sglu.support.BancoDeTeste;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DespachoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProdutoRepository produtos;
    @Autowired ClienteRepository clientes;
    @Autowired UsuarioRepository usuarios;
    @Autowired PedidoService pedidos;
    @Autowired EstoqueService estoque;
    @Autowired PasswordEncoder encoder;
    Long admin, operador, cliente, p1, p2, p3;
    String token;
    static final String SENHA = "senha-ficticia-123";
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @BeforeEach
    void preparar() throws Exception {
        BancoDeTeste.limpar(jdbc);
        String hash = encoder.encode(SENHA);
        admin = usuarios.saveAndFlush(new Usuario("Admin", "admin@example.com", hash, Perfil.ADMINISTRADOR)).getId();
        operador = usuarios.saveAndFlush(new Usuario("Operador", "op@example.com", hash, Perfil.OPERADOR)).getId();
        cliente = clientes.saveAndFlush(new Cliente("Cliente", "123", "Rua fictícia")).getId();
        p1 = produtos.saveAndFlush(new Produto("P1", "Produto um", 2)).getId();
        p2 = produtos.saveAndFlush(new Produto("P2", "Produto dois", 2)).getId();
        p3 = produtos.saveAndFlush(new Produto("P3", "Produto três", 2)).getId();
        token = login("admin@example.com");
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void baixaTodosItensUmaVezComAutoriaPedidoEHistoricoNaMesmaTransacao(Perfil perfil) throws Exception {
        entrada(p1, 5); entrada(p2, 3);
        long id = prepararPedido(item(p1, 5), item(p2, 2));
        String jwt = perfil == Perfil.ADMINISTRADOR ? token : login("op@example.com");
        despachar(id, jwt).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DESPACHADO"));
        assertThat(saldo(p1)).isZero(); assertThat(saldo(p2)).isEqualTo(1);
        assertThat(saidas(id)).isEqualTo(2);
        Long autor = perfil == Perfil.ADMINISTRADOR ? admin : operador;
        assertThat(jdbc.queryForList("SELECT usuario_id FROM movimentacoes_estoque WHERE pedido_id=?", Long.class, id))
                .containsOnly(autor);
        assertThat(jdbc.queryForObject("SELECT SUM(quantidade) FROM movimentacoes_estoque WHERE pedido_id=?", Integer.class, id)).isEqualTo(7);
        var resposta = pedidos.detalhar(id);
        var registro = resposta.historico().getLast();
        assertThat(registro.status()).isEqualTo(StatusPedido.DESPACHADO);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque WHERE pedido_id=? AND registrado_em=? AND data=?", Integer.class,
                id, java.sql.Timestamp.from(registro.dataHora()), registro.dataHora().atZone(FUSO).toLocalDate())).isEqualTo(2);
        despachar(id, jwt).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("STATUS_DESATUALIZADO"));
        pedidos.avancar(id, StatusPedido.DESPACHADO, autor);
        pedidos.avancar(id, StatusPedido.EM_TRANSITO, autor);
        assertThat(saidas(id)).isEqualTo(2); assertThat(saldo(p1)).isZero();
        assertThat(pedidos.detalhar(id).historico()).hasSize(5);
    }

    @Test
    void listaTodasFaltasSemBaixaParcialEPermiteReporETentarNovamente() throws Exception {
        entrada(p1, 10); entrada(p2, 1);
        long id = prepararPedido(item(p1, 2), item(p2, 2), item(p3, 3));
        despachar(id, token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTOQUE_INSUFICIENTE"))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].produtoId").value(p2))
                .andExpect(jsonPath("$.itens[0].solicitado").value(2))
                .andExpect(jsonPath("$.itens[0].disponivel").value(1))
                .andExpect(jsonPath("$.itens[0].faltante").value(1))
                .andExpect(jsonPath("$.itens[1].produtoId").value(p3))
                .andExpect(jsonPath("$.itens[1].faltante").value(3));
        assertThat(saldo(p1)).isEqualTo(10); assertThat(saldo(p2)).isEqualTo(1); assertThat(saldo(p3)).isZero();
        semDespacho(id);
        entrada(p2, 1); entrada(p3, 3);
        despachar(id, token).andExpect(status().isOk());
        assertThat(saldo(p1)).isEqualTo(8); assertThat(saldo(p2)).isZero(); assertThat(saldo(p3)).isZero();
        assertThat(saidas(id)).isEqualTo(3);
    }

    @Test
    void criacaoSeparacaoECancelamentoNaoReservamNemBaixamEstoque() {
        entrada(p1, 1);
        long id = prepararPedido(item(p1, 100));
        pedidos.cancelar(id, admin);
        assertThat(saldo(p1)).isEqualTo(1); assertThat(saidas(id)).isZero();
        assertThat(pedidos.detalhar(id).status()).isEqualTo(StatusPedido.CANCELADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"movimentacoes_estoque", "pedido_historico"})
    void falhaRealDePersistenciaDesfazSaldosSaidasStatusEHistorico(String tabela) throws Exception {
        entrada(p1, 5); entrada(p2, 5);
        long id = prepararPedido(item(p1, 2), item(p2, 3));
        String condicao = tabela.equals("movimentacoes_estoque")
                ? "tipo <> 'SAIDA' OR produto_id <> " + p2 : "status <> 'DESPACHADO'";
        jdbc.execute("ALTER TABLE " + tabela + " ADD CONSTRAINT teste_recusa_despacho CHECK (" + condicao + ")");
        try {
            despachar(id, token).andExpect(status().isConflict());
            assertThat(saldo(p1)).isEqualTo(5); assertThat(saldo(p2)).isEqualTo(5);
            semDespacho(id);
        } finally { jdbc.execute("ALTER TABLE " + tabela + " DROP CONSTRAINT teste_recusa_despacho"); }
    }

    @Test
    void duasTentativasSimultaneasDoMesmoPedidoNaoDuplicamBaixa() throws Exception {
        entrada(p1, 10);
        long id = prepararPedido(item(p1, 4));
        assertThat(simultaneas(() -> avancar(id), () -> avancar(id)))
                .containsExactlyInAnyOrder("DESPACHADO", "STATUS_DESATUALIZADO");
        assertThat(saldo(p1)).isEqualTo(6); assertThat(saidas(id)).isEqualTo(1);
        assertThat(pedidos.detalhar(id).historico()).hasSize(3);
    }

    @Test
    void pedidosConcorrentesComItensInvertidosNaoPerdemSaldoNemFazemBaixaParcial() throws Exception {
        entrada(p1, 5); entrada(p2, 5);
        long a = prepararPedido(item(p1, 4), item(p2, 4));
        long b = prepararPedido(item(p2, 4), item(p1, 4));
        assertThat(simultaneas(() -> avancar(a), () -> avancar(b)))
                .containsExactlyInAnyOrder("DESPACHADO", "ESTOQUE_INSUFICIENTE");
        assertThat(saldo(p1)).isEqualTo(1); assertThat(saldo(p2)).isEqualTo(1);
        assertThat(saidas(a) + saidas(b)).isEqualTo(2);
        assertThat(pedidos.detalhar(a).historico().size() + pedidos.detalhar(b).historico().size()).isEqualTo(5);
    }

    @Test
    void entradaConcorrenteComDespachoMantemSaldoCorreto() throws Exception {
        entrada(p1, 5);
        long id = prepararPedido(item(p1, 4));
        assertThat(simultaneas(() -> avancar(id), () -> { entrada(p1, 5); return "ENTRADA"; }))
                .containsExactlyInAnyOrder("DESPACHADO", "ENTRADA");
        assertThat(saldo(p1)).isEqualTo(6); assertThat(saidas(id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"editar", "cancelar"})
    void mutacaoConcorrenteDoPedidoNaoDesalinhaItensStatusESaida(String acao) throws Exception {
        entrada(p1, 4);
        long id = prepararPedido(item(p1, 4));
        var resultados = simultaneas(() -> avancar(id), () -> {
            try {
                if (acao.equals("editar")) pedidos.editar(id, dados(item(p1, 1)));
                else pedidos.cancelar(id, admin);
                return acao;
            } catch (OperacaoNaoPermitidaException erro) { return erro.getCodigo(); }
        });
        var pedido = pedidos.detalhar(id);
        if (pedido.status() == StatusPedido.CANCELADO) {
            assertThat(resultados).containsExactlyInAnyOrder("cancelar", "STATUS_DESATUALIZADO");
            assertThat(saldo(p1)).isEqualTo(4); assertThat(saidas(id)).isZero();
        } else {
            assertThat(pedido.status()).isEqualTo(StatusPedido.DESPACHADO);
            int quantidade = pedido.itens().getFirst().quantidade();
            assertThat(saldo(p1)).isEqualTo(4 - quantidade);
            assertThat(jdbc.queryForObject("SELECT quantidade FROM movimentacoes_estoque WHERE pedido_id=?", Integer.class, id)).isEqualTo(quantidade);
            assertThat(saidas(id)).isEqualTo(1);
        }
    }

    @Test
    void despachoSemTokenNaoAlteraPedidoOuEstoque() throws Exception {
        entrada(p1, 5);
        long id = prepararPedido(item(p1, 2));
        mvc.perform(post("/api/pedidos/" + id + "/avancar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusAtual\":\"EM_SEPARACAO\"}")).andExpect(status().isUnauthorized());
        assertThat(saldo(p1)).isEqualTo(5); semDespacho(id);
    }

    private List<String> simultaneas(Callable<String> primeira, Callable<String> segunda) throws Exception {
        var inicio = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { inicio.await(10, TimeUnit.SECONDS); return primeira.call(); });
            var b = executor.submit(() -> { inicio.await(10, TimeUnit.SECONDS); return segunda.call(); });
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }
    private String avancar(long id) {
        try { return pedidos.avancar(id, StatusPedido.EM_SEPARACAO, admin).status().name(); }
        catch (EstoqueInsuficienteException erro) { return "ESTOQUE_INSUFICIENTE"; }
        catch (OperacaoNaoPermitidaException erro) { return erro.getCodigo(); }
    }
    private void entrada(Long produto, int quantidade) { estoque.registrarEntrada(new EntradaEstoqueRequest(produto, quantidade, LocalDate.now(FUSO)), admin); }
    private int saldo(Long id) { return produtos.findById(id).orElseThrow().getSaldo(); }
    private int saidas(long pedido) { return jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque WHERE pedido_id=? AND tipo='SAIDA'", Integer.class, pedido); }
    private void semDespacho(long id) {
        assertThat(saidas(id)).isZero();
        var pedido = pedidos.detalhar(id);
        assertThat(pedido.status()).isEqualTo(StatusPedido.EM_SEPARACAO);
        assertThat(pedido.historico()).hasSize(2);
    }
    private long prepararPedido(ItemPedidoRequest... itens) {
        long id = pedidos.criar(dados(itens), admin).id();
        pedidos.avancar(id, StatusPedido.CRIADO, admin);
        return id;
    }
    private PedidoRequest dados(ItemPedidoRequest... itens) { return new PedidoRequest(cliente, "Rua fictícia", LocalDate.now(FUSO).plusDays(2), List.of(itens)); }
    private ItemPedidoRequest item(Long id, int quantidade) { return new ItemPedidoRequest(id, quantidade); }
    private ResultActions despachar(long id, String jwt) throws Exception {
        return mvc.perform(post("/api/pedidos/" + id + "/avancar").header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"statusAtual\":\"EM_SEPARACAO\"}"));
    }
    private String login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "senha", SENHA))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("token").asText();
    }
}
