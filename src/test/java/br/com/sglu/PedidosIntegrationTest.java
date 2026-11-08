package br.com.sglu;

import br.com.sglu.model.Cliente;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Produto;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.ClienteRepository;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidosIntegrationTest {
    private static final String PASSWORD = "senha-ficticia-123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    String adminToken;
    String operadorToken;
    Long clienteA;
    Long clienteB;
    Long caixa;
    Long fita;

    @BeforeEach
    void setUp() throws Exception {
        apagarPedidos();
        produtos.deleteAll();
        clientes.deleteAll();
        usuarios.deleteAll();
        String hash = passwords.encode(PASSWORD);
        usuarios.save(new Usuario("Administrador", "admin@example.com", hash, Perfil.ADMINISTRADOR));
        usuarios.save(new Usuario("Operador", "operador@example.com", hash, Perfil.OPERADOR));
        adminToken = login("admin@example.com");
        operadorToken = login("operador@example.com");
        clienteA = clientes.save(new Cliente("Comercial Silva", "(15) 3221-4400", "Rua das Flores, 120")).getId();
        clienteB = clientes.save(new Cliente("Padaria Estrela", "(15) 3212-8890", "Rua Sete, 85")).getId();
        caixa = produtos.save(new Produto("CX-M", "Caixa média", 5)).getId();
        fita = produtos.save(new Produto("FIT-48", "Fita 48 mm", 10)).getId();
    }

    @AfterEach
    void apagarPedidos() {
        jdbc.update("DELETE FROM pedidos");
    }

    @Test
    void operadorCriaPedidoComStatusCriadoCodigoDeConsultaEHistorico() throws Exception {
        criar(pedido(clienteA, hoje().plusDays(3), item(caixa, 2), item(fita, 1)), operadorToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.codigo").value(matchesPattern("[A-HJ-NP-Z2-9]{8}")))
                .andExpect(jsonPath("$.status").value("CRIADO"))
                .andExpect(jsonPath("$.cliente.nome").value("Comercial Silva"))
                .andExpect(jsonPath("$.enderecoEntrega").value("Rua de entrega, 10"))
                .andExpect(jsonPath("$.prazoEntrega").value(hoje().plusDays(3).toString()))
                .andExpect(jsonPath("$.atrasado").value(false))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].produto.codigo").value("CX-M"))
                .andExpect(jsonPath("$.itens[0].quantidade").value(2))
                .andExpect(jsonPath("$.historico.length()").value(1))
                .andExpect(jsonPath("$.historico[0].status").value("CRIADO"))
                .andExpect(jsonPath("$.historico[0].usuario.nome").value("Operador"))
                .andExpect(jsonPath("$.historico[0].dataHora").isString());
    }

    @Test
    void listaMaisRecentesPrimeiroComFiltrosCombinaveisEAtraso() throws Exception {
        long primeiro = criarId(clienteA, hoje().plusDays(3));
        long segundo = criarId(clienteB, hoje().plusDays(3));
        long terceiro = criarId(clienteA, hoje().plusDays(3));
        avancar(terceiro, "CRIADO", adminToken).andExpect(status().isOk());
        jdbc.update("UPDATE pedidos SET prazo_entrega = ? WHERE id = ?", hoje().minusDays(1), primeiro);

        listar("").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(terceiro))
                .andExpect(jsonPath("$[1].id").value(segundo))
                .andExpect(jsonPath("$[2].id").value(primeiro))
                .andExpect(jsonPath("$[0].codigo").isString())
                .andExpect(jsonPath("$[0].cliente.nome").value("Comercial Silva"))
                .andExpect(jsonPath("$[0].status").value("EM_SEPARACAO"))
                .andExpect(jsonPath("$[0].atrasado").value(false))
                .andExpect(jsonPath("$[2].atrasado").value(true));
        listar("?status=CRIADO").andExpect(jsonPath("$.length()").value(2));
        listar("?clienteId=" + clienteA).andExpect(jsonPath("$.length()").value(2));
        listar("?status=CRIADO&clienteId=" + clienteA)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(primeiro));
        listar("?status=ENTREGUE").andExpect(jsonPath("$.length()").value(0));
        listar("?status=INEXISTENTE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void avancaUmaEtapaPorVezAteEntregue() throws Exception {
        long id = criarId(clienteA, hoje().plusDays(3));
        String atual = "CRIADO";
        for (String proximo : List.of("EM_SEPARACAO", "DESPACHADO", "EM_TRANSITO", "ENTREGUE")) {
            avancar(id, atual, operadorToken).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(proximo));
            atual = proximo;
        }
        avancar(id, "EM_TRANSITO", operadorToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STATUS_DESATUALIZADO"));
        avancar(id, "ENTREGUE", operadorToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_SEM_PROXIMO_STATUS"));
        mvc.perform(comToken(post("/api/pedidos/" + id + "/avancar"), operadorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalhes.statusAtual").isString());

        mvc.perform(comToken(get("/api/pedidos/" + id), adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.historico[*].status")
                        .value(contains("CRIADO", "EM_SEPARACAO", "DESPACHADO", "EM_TRANSITO", "ENTREGUE")))
                .andExpect(jsonPath("$.historico[4].usuario.nome").value("Operador"));
    }

    @Test
    void editaECancelaSomenteAntesDoDespacho() throws Exception {
        long id = criarId(clienteA, hoje().plusDays(3));
        avancar(id, "CRIADO", adminToken).andExpect(status().isOk());

        editar(id, pedido(clienteB, hoje().plusDays(5), item(fita, 4), item(caixa, 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_SEPARACAO"))
                .andExpect(jsonPath("$.cliente.nome").value("Padaria Estrela"))
                .andExpect(jsonPath("$.prazoEntrega").value(hoje().plusDays(5).toString()));
        mvc.perform(comToken(get("/api/pedidos/" + id), adminToken))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].produto.codigo").value("CX-M"))
                .andExpect(jsonPath("$.itens[0].quantidade").value(1))
                .andExpect(jsonPath("$.itens[1].produto.codigo").value("FIT-48"))
                .andExpect(jsonPath("$.itens[1].quantidade").value(4));
        editar(id, pedido(clienteB, hoje().plusDays(5), item(fita, 2))).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1));

        cancelar(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.historico[2].status").value("CANCELADO"));
        editar(id, pedido(clienteA, hoje().plusDays(5), item(caixa, 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_NAO_EDITAVEL"));

        long despachado = criarId(clienteA, hoje().plusDays(3));
        avancar(despachado, "CRIADO", adminToken).andExpect(status().isOk());
        avancar(despachado, "EM_SEPARACAO", adminToken).andExpect(status().isOk());
        cancelar(despachado).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PEDIDO_NAO_CANCELAVEL"));
        editar(despachado, pedido(clienteA, hoje().plusDays(5), item(caixa, 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_NAO_EDITAVEL"));
    }

    @Test
    void validaCamposEReferenciasSemCriarPedido() throws Exception {
        criar(Map.of(), adminToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.detalhes.clienteId").isString())
                .andExpect(jsonPath("$.detalhes.enderecoEntrega").isString())
                .andExpect(jsonPath("$.detalhes.prazoEntrega").isString())
                .andExpect(jsonPath("$.detalhes.itens").value("Informe ao menos um item"));
        criar(pedido(clienteA, hoje().plusDays(1), item(caixa, 0)), adminToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes['itens[0].quantidade']").value("Quantidade deve ser maior que zero"));
        criar(pedido(999_999L, hoje().minusDays(1), item(caixa, 1), item(888_888L, 1), item(caixa, 2)), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes.clienteId").value("Cliente não encontrado"))
                .andExpect(jsonPath("$.detalhes['itens[1].produtoId']").value("Produto não encontrado"))
                .andExpect(jsonPath("$.detalhes['itens[2].produtoId']").value("Produto repetido no pedido"))
                .andExpect(jsonPath("$.detalhes.prazoEntrega").value("O prazo de entrega não pode ser anterior a hoje"));
        criar(pedido(clienteA, hoje().plusDays(1), item(caixa, 1.5)), adminToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
        var dataInvalida = pedido(clienteA, hoje().plusDays(1), item(caixa, 1));
        dataInvalida.put("prazoEntrega", "31/12/2026");
        criar(dataInvalida, adminToken).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedidos", Integer.class)).isZero();
    }

    @Test
    void exigeAutenticacaoEIndicaPedidoInexistente() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(pedido(clienteA, hoje().plusDays(1), item(caixa, 1)))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pedidos/1/avancar").contentType(MediaType.APPLICATION_JSON).content("{\"statusAtual\":\"CRIADO\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(comToken(get("/api/pedidos/999999"), adminToken)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_NAO_ENCONTRADO"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedidos", Integer.class)).isZero();
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    @SafeVarargs
    private static Map<String, Object> pedido(Long clienteId, LocalDate prazo, Map<String, Object>... itens) {
        var corpo = new LinkedHashMap<String, Object>();
        corpo.put("clienteId", clienteId);
        corpo.put("enderecoEntrega", " Rua de entrega, 10 ");
        corpo.put("prazoEntrega", prazo.toString());
        corpo.put("itens", List.of(itens));
        return corpo;
    }

    private static Map<String, Object> item(Long produtoId, Object quantidade) {
        return Map.of("produtoId", produtoId, "quantidade", quantidade);
    }

    private long criarId(Long clienteId, LocalDate prazo) throws Exception {
        String resposta = criar(pedido(clienteId, prazo, item(caixa, 2)), adminToken)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("id").asLong();
    }

    private ResultActions criar(Object corpo, String token) throws Exception {
        return mvc.perform(comToken(post("/api/pedidos"), token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)));
    }

    private ResultActions editar(long id, Object corpo) throws Exception {
        return mvc.perform(comToken(put("/api/pedidos/" + id), operadorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)));
    }

    private ResultActions cancelar(long id) throws Exception {
        return mvc.perform(comToken(post("/api/pedidos/" + id + "/cancelar"), operadorToken));
    }

    private ResultActions avancar(long id, String statusAtual, String token) throws Exception {
        return mvc.perform(comToken(post("/api/pedidos/" + id + "/avancar"), token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("statusAtual", statusAtual))));
    }

    private ResultActions listar(String filtros) throws Exception {
        return mvc.perform(comToken(get("/api/pedidos" + filtros), operadorToken));
    }

    private static MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String login(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }
}
