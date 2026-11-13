package br.com.sglu;

import br.com.sglu.dto.pedido.AvancoStatusRequest;
import br.com.sglu.dto.pedido.ItemPedidoRequest;
import br.com.sglu.dto.pedido.OcorrenciaPedidoRequest;
import br.com.sglu.dto.pedido.PedidoRequest;
import br.com.sglu.model.Cliente;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Produto;
import br.com.sglu.model.StatusPedido;
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
class PedidoTimelineIntegrationTest {

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

    String adminToken;
    String operadorToken;
    Cliente cliente;
    Produto produto;

    @BeforeEach
    void setUp() throws Exception {
        BancoDeTeste.limpar(jdbc);

        String hash = passwords.encode(PASSWORD);
        usuarios.save(new Usuario("Admin Timeline", "admin@example.com", hash, Perfil.ADMINISTRADOR));
        usuarios.save(new Usuario("Operador Timeline", "operador@example.com", hash, Perfil.OPERADOR));

        adminToken = login("admin@example.com");
        operadorToken = login("operador@example.com");

        cliente = clientes.save(new Cliente("Cliente Teste", "11999999999", "cliente@example.com"));
        produto = produtos.save(new Produto("PROD-TL", "Produto Timeline", 5));
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
    void deveExibirTimelineCompletaComStatusEOcorrenciasEmOrdemCronologica() throws Exception {
        // 1. Cria o pedido (gera status CRIADO pelo Admin)
        var pedidoReq = new PedidoRequest(
                cliente.getId(),
                "Rua das Flores, 123",
                LocalDate.now().plusDays(5),
                List.of(new ItemPedidoRequest(produto.getId(), 2)));
        var criacaoResp = mvc.perform(post("/api/pedidos")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(pedidoReq)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long pedidoId = json.readTree(criacaoResp).get("id").asLong();

        // 2. Registra uma ocorrência antes da separação (pelo Operador)
        var ocorrencia1 = new OcorrenciaPedidoRequest("Cliente solicitou entrega no período da manhã");
        mvc.perform(post("/api/pedidos/{id}/ocorrencias", pedidoId)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ocorrencia1)))
                .andExpect(status().isCreated());

        // 3. Avança o status para EM_SEPARACAO (pelo Operador)
        var avanco = new AvancoStatusRequest(StatusPedido.CRIADO);
        mvc.perform(post("/api/pedidos/{id}/avancar", pedidoId)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(avanco)))
                .andExpect(status().isOk());

        // 4. Registra segunda ocorrência durante a separação (pelo Admin)
        var ocorrencia2 = new OcorrenciaPedidoRequest("Item conferido e embalado com proteção extra");
        mvc.perform(post("/api/pedidos/{id}/ocorrencias", pedidoId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ocorrencia2)))
                .andExpect(status().isCreated());

        // 5. Consulta a timeline completa e verifica ordem cronológica e dados
        mvc.perform(get("/api/pedidos/{id}/timeline", pedidoId)
                .header("Authorization", "Bearer " + operadorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].tipo").value("STATUS"))
                .andExpect(jsonPath("$[0].status").value("CRIADO"))
                .andExpect(jsonPath("$[0].usuario.nome").value("Admin Timeline"))
                .andExpect(jsonPath("$[0].dataHora").isNotEmpty())
                .andExpect(jsonPath("$[1].tipo").value("OCORRENCIA"))
                .andExpect(jsonPath("$[1].descricao").value("Cliente solicitou entrega no período da manhã"))
                .andExpect(jsonPath("$[1].usuario.nome").value("Operador Timeline"))
                .andExpect(jsonPath("$[2].tipo").value("STATUS"))
                .andExpect(jsonPath("$[2].status").value("EM_SEPARACAO"))
                .andExpect(jsonPath("$[2].usuario.nome").value("Operador Timeline"))
                .andExpect(jsonPath("$[3].tipo").value("OCORRENCIA"))
                .andExpect(jsonPath("$[3].descricao").value("Item conferido e embalado com proteção extra"))
                .andExpect(jsonPath("$[3].usuario.nome").value("Admin Timeline"));
    }

    @Test
    void deveRetornar404QuandoPedidoNaoExistir() throws Exception {
        mvc.perform(get("/api/pedidos/{id}/timeline", 99999L)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}