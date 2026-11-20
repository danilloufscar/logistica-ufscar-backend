package br.com.sglu;

import br.com.sglu.dto.pedido.AvancoStatusRequest;
import br.com.sglu.dto.pedido.ItemPedidoRequest;
import br.com.sglu.dto.pedido.OcorrenciaPedidoRequest;
import br.com.sglu.dto.pedido.PedidoRequest;
import br.com.sglu.model.Cliente;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Produto;
import br.com.sglu.model.StatusPedido;
import br.com.sglu.model.TipoOcorrencia;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.ClienteRepository;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
import br.com.sglu.support.BancoDeTeste;
import java.time.LocalDate;
import java.util.List;
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
class OcorrenciaPedidoIntegrationTest {

    private static final String PASSWORD = "senha-ficticia-123";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    ClienteRepository clientes;
    @Autowired
    ProdutoRepository produtos;
    @Autowired
    PasswordEncoder passwords;
    @Autowired
    JdbcTemplate jdbc;

    String operadorToken;
    Cliente cliente;
    Produto produto;

    @BeforeEach
    void setUp() throws Exception {
        BancoDeTeste.limpar(jdbc);

        String hash = passwords.encode(PASSWORD);
        usuarios.save(new Usuario("Operador Ocorrencia", "operador@example.com", hash, Perfil.OPERADOR));
        operadorToken = login("operador@example.com");

        cliente = clientes.save(new Cliente("Cliente Ocorrencia", "11999999999", "cliente@example.com"));
        produto = produtos.save(new Produto("PROD-OC", "Produto Ocorrencia", 10));
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

    private Long criarPedidoEmSeparacao() throws Exception {
        var pedidoReq = new PedidoRequest(
                cliente.getId(),
                "Av. Paulista, 1000",
                LocalDate.now().plusDays(3),
                List.of(new ItemPedidoRequest(produto.getId(), 1)));
        var resp = mvc.perform(post("/api/pedidos")
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(pedidoReq)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long id = json.readTree(resp).get("id").asLong();

        mvc.perform(post("/api/pedidos/{id}/avancar", id)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AvancoStatusRequest(StatusPedido.CRIADO))))
                .andExpect(status().isOk());

        return id;
    }

    @Test
    void deveRegistrarAtrasoAvariaEDestinatarioAusenteSemAlterarStatusDoPedido() throws Exception {
        Long pedidoId = criarPedidoEmSeparacao();
        LocalDate dataOcorrencia = LocalDate.now();

        var atraso = new OcorrenciaPedidoRequest(TipoOcorrencia.ATRASO, "Trânsito intenso na rodovia", dataOcorrencia);
        mvc.perform(post("/api/pedidos/{id}/ocorrencias", pedidoId)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(atraso)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoOcorrencia").value("ATRASO"))
                .andExpect(jsonPath("$.tipoOcorrenciaRotulo").value("Atraso"))
                .andExpect(jsonPath("$.dataOcorrencia").value(dataOcorrencia.toString()));

        var avaria = new OcorrenciaPedidoRequest(TipoOcorrencia.AVARIA, "Embalagem externa amassada", dataOcorrencia);
        mvc.perform(post("/api/pedidos/{id}/ocorrencias", pedidoId)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(avaria)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoOcorrencia").value("AVARIA"))
                .andExpect(jsonPath("$.tipoOcorrenciaRotulo").value("Avaria"));

        var ausente = new OcorrenciaPedidoRequest(TipoOcorrencia.DESTINATARIO_AUSENTE,
                "Portaria fechada na tentativa de entrega", dataOcorrencia);
        mvc.perform(post("/api/pedidos/{id}/ocorrencias", pedidoId)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ausente)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoOcorrencia").value("DESTINATARIO_AUSENTE"))
                .andExpect(jsonPath("$.tipoOcorrenciaRotulo").value("Destinatário ausente"));

        // Garante que o status do pedido permaneceu exatamente EM_SEPARACAO
        mvc.perform(get("/api/pedidos/{id}", pedidoId)
                .header("Authorization", "Bearer " + operadorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_SEPARACAO"));
    }

    @Test
    void deveRejeitarOcorrenciaSemTipoDescricaoOuData() throws Exception {
        Long pedidoId = criarPedidoEmSeparacao();

        mvc.perform(post("/api/pedidos/{id}/ocorrencias", pedidoId)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\": \"Sem tipo e sem data\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarOcorrenciaEmPedidoEncerrado() throws Exception {
        Long pedidoId = criarPedidoEmSeparacao();

        // Cancela o pedido (status encerrado: CANCELADO)
        mvc.perform(post("/api/pedidos/{id}/cancelar", pedidoId)
                .header("Authorization", "Bearer " + operadorToken))
                .andExpect(status().isOk());

        var req = new OcorrenciaPedidoRequest(TipoOcorrencia.ATRASO, "Tentativa após cancelamento", LocalDate.now());
        mvc.perform(post("/api/pedidos/{id}/ocorrencias", pedidoId)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejeitaDescricaoVaziaLongaTipoInvalidoESemAutenticacaoSemGravar() throws Exception {
        Long id = criarPedidoEmSeparacao();
        for (var dados : List.of(
                Map.of("tipo", "ATRASO", "descricao", "   ", "data", "2026-11-15"),
                Map.of("tipo", "ATRASO", "descricao", "x".repeat(501), "data", "2026-11-15"),
                Map.of("tipo", "INVALIDO", "descricao", "Teste", "data", "2026-11-15"))) {
            mvc.perform(post("/api/pedidos/{id}/ocorrencias", id)
                    .header("Authorization", "Bearer " + operadorToken)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dados)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/pedidos/{id}/ocorrencias", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new OcorrenciaPedidoRequest(TipoOcorrencia.AVARIA, "Teste", LocalDate.now()))))
                .andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from pedido_ocorrencias where pedido_id = ?", Long.class, id)).isZero();
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void ocorrenciaAguardaEncerramentoConcorrenteENaoGravaDepoisDeCancelado() throws Exception {
        Long id = criarPedidoEmSeparacao();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var iniciou = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var futuro = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<Integer>>();
            tx.executeWithoutResult(estado -> {
                jdbc.queryForObject("select id from pedidos where id = ? for update", Long.class, id);
                futuro.set(executor.submit(() -> {
                    iniciou.countDown();
                    return mvc.perform(post("/api/pedidos/{id}/ocorrencias", id)
                            .header("Authorization", "Bearer " + operadorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new OcorrenciaPedidoRequest(
                                    TipoOcorrencia.ATRASO, "Concorrência", LocalDate.now()))))
                            .andReturn().getResponse().getStatus();
                }));
                try {
                    org.assertj.core.api.Assertions.assertThat(iniciou.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> futuro.get().get(300, java.util.concurrent.TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                jdbc.update("update pedidos set status = 'CANCELADO' where id = ?", id);
            });
            org.assertj.core.api.Assertions.assertThat(futuro.get().get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(409);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                    "select count(*) from pedido_ocorrencias where pedido_id = ?", Long.class, id)).isZero();
        }
    }}
