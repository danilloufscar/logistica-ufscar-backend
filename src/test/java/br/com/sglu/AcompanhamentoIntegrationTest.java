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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AcompanhamentoIntegrationTest {

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
        usuarios.save(new Usuario("Operador Acompanhamento", "operador@example.com", hash, Perfil.OPERADOR));
        operadorToken = login("operador@example.com");

        cliente = clientes.save(new Cliente("Cliente Publico", "11999999999", "cliente@example.com"));
        produto = produtos.save(new Produto("PROD-AC", "Produto Acompanhamento", 10));
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
    void deveRetornarDadosAtualizadosSemCacheParaPollingDe30Segundos() throws Exception {
        var pedidoReq = new PedidoRequest(
                cliente.getId(),
                "Rua Vergueiro, 500",
                LocalDate.now().plusDays(4),
                List.of(new ItemPedidoRequest(produto.getId(), 2)));
        var criacaoResp = mvc.perform(post("/api/pedidos")
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(pedidoReq)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long pedidoId = json.readTree(criacaoResp).get("id").asLong();
        String codigo = json.readTree(criacaoResp).get("codigo").asText();

        // 1ª consulta pública (sem token JWT)
        mvc.perform(get("/api/acompanhamento/{codigo}", codigo))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.status").value("CRIADO"))
                .andExpect(jsonPath("$.intervaloAtualizacaoSegundos").value(30))
                .andExpect(jsonPath("$.timeline.length()").value(1));

        // Simula atualização no pedido (avanço de status + ocorrência) antes do próximo
        // ciclo de 30s
        mvc.perform(post("/api/pedidos/{id}/avancar", pedidoId)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AvancoStatusRequest(StatusPedido.CRIADO))))
                .andExpect(status().isOk());

        var ocorrencia = new OcorrenciaPedidoRequest(TipoOcorrencia.ATRASO, "Chuva forte na região", LocalDate.now());
        mvc.perform(post("/api/pedidos/{id}/ocorrencias", pedidoId)
                .header("Authorization", "Bearer " + operadorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ocorrencia)))
                .andExpect(status().isCreated());

        // 2ª consulta pública (ciclo seguinte do polling de 30s): deve refletir as
        // mudanças imediatamente
        mvc.perform(get("/api/acompanhamento/{codigo}", codigo.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.status").value("EM_SEPARACAO"))
                .andExpect(jsonPath("$.timeline.length()").value(3))
                .andExpect(jsonPath("$.timeline[2].tipoOcorrencia").value("ATRASO"));
    }

    @Test
    void deveRetornar404QuandoCodigoNaoExistir() throws Exception {
        mvc.perform(get("/api/acompanhamento/{codigo}", "XXXX9999"))
                .andExpect(status().isNotFound());
    }
}