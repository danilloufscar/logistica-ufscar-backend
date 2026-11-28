package br.com.sglu;

import br.com.sglu.model.*;
import br.com.sglu.repository.*;
import br.com.sglu.support.BancoDeTeste;
import java.time.Instant;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RelatorioIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired PedidoRepository pedidos;
    @Autowired PasswordEncoder encoder;
    Usuario admin;
    Cliente cliente;
    Produto produto;
    String token;
    int sequencia;

    @BeforeEach
    void preparar() throws Exception {
        BancoDeTeste.limpar(jdbc);
        admin = usuarios.save(new Usuario("Admin Relatório", "admin.rel@example.com", encoder.encode("senha-teste-123"), Perfil.ADMINISTRADOR));
        cliente = clientes.save(new Cliente("Cliente fictício", "11999999999", "Rua fictícia"));
        produto = produtos.save(new Produto("REL-1", "Produto fictício", 0));
        token = login(admin.getEmail());
    }

    private String login(String email) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "senha", "senha-teste-123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    private void pedido(String criado, String entregue, String prazo, boolean cancelado) {
        var p = new Pedido(String.format("R%07d", ++sequencia), cliente, "Rua fictícia",
                LocalDate.parse(prazo), List.of(new Pedido.Item(produto, 1)), admin, Instant.parse(criado));
        if (cancelado) p.cancelar(admin, Instant.parse(criado).plusSeconds(1));
        else if (entregue != null) {
            for (int i = 0; i < 4; i++) p.avancar(admin, Instant.parse(entregue));
        }
        pedidos.saveAndFlush(p);
    }

    @Test
    void calculaIndicadoresComPeriodosInclusivosFusoECancelados() throws Exception {
        pedido("2026-11-01T03:00:00Z", "2026-11-10T02:59:59Z", "2026-11-09", false);
        pedido("2026-10-20T03:00:00Z", "2026-11-11T03:00:00Z", "2026-11-10", false);
        pedido("2026-11-30T02:00:00Z", "2026-12-01T02:59:59Z", "2026-11-30", false);
        pedido("2026-11-30T03:00:00Z", null, "2026-12-10", false);
        pedido("2026-11-15T03:00:00Z", null, "2026-12-10", true);
        pedido("2026-12-01T03:00:00Z", null, "2026-12-10", false);
        pedido("2026-11-01T02:59:59Z", null, "2026-12-10", false);
        // Entrega exatamente no início do dia seguinte não entra na pontualidade de novembro.
        pedido("2026-10-20T03:00:00Z", "2026-12-01T03:00:00Z", "2026-12-01", false);
        mvc.perform(get("/api/relatorios/desempenho").param("inicio", "2026-11-01").param("fim", "2026-11-30")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPedidos").value(3))
                .andExpect(jsonPath("$.pedidosPorStatus.length()").value(5))
                .andExpect(jsonPath("$.pedidosPorStatus[?(@.status == 'CRIADO')].quantidade", contains(1)))
                .andExpect(jsonPath("$.pedidosPorStatus[?(@.status == 'ENTREGUE')].quantidade", contains(2)))
                .andExpect(jsonPath("$.pedidosPorStatus[?(@.status == 'CANCELADO')]").isEmpty())
                .andExpect(jsonPath("$.entregasNoPeriodo").value(3))
                .andExpect(jsonPath("$.entregasNoPrazo").value(2))
                .andExpect(jsonPath("$.percentualNoPrazo").value(66.67))
                .andExpect(jsonPath("$.tempoMedioEntregaDias").value(10.68));
    }

    @Test
    void periodoSemEntregasRetornaPercentualNuloEContagensZero() throws Exception {
        mvc.perform(get("/api/relatorios/desempenho").param("inicio", "2026-11-01").param("fim", "2026-11-01")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPedidos").value(0))
                .andExpect(jsonPath("$.entregasNoPeriodo").value(0))
                .andExpect(jsonPath("$.percentualNoPrazo").value(nullValue()))
                .andExpect(jsonPath("$.tempoMedioEntregaDias").value(nullValue()));
    }

    @Test
    void rejeitaPeriodoInvertidoAusenteOuMalformado() throws Exception {
        mvc.perform(get("/api/relatorios/desempenho").param("inicio", "2026-11-30").param("fim", "2026-11-01")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/relatorios/desempenho").param("inicio", "2026-11-01")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/relatorios/desempenho").param("inicio", "invalida").param("fim", "2026-11-30")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }

    @Test
    void bloqueiaOperadorENaoAutenticado() throws Exception {
        usuarios.save(new Usuario("Operador", "operador.rel@example.com", encoder.encode("senha-teste-123"), Perfil.OPERADOR));
        var operador = login("operador.rel@example.com");
        mvc.perform(get("/api/relatorios/desempenho").param("inicio", "2026-11-01").param("fim", "2026-11-30")
                .header("Authorization", "Bearer " + operador)).andExpect(status().isForbidden());
        mvc.perform(get("/api/relatorios/desempenho").param("inicio", "2026-11-01").param("fim", "2026-11-30"))
                .andExpect(status().isUnauthorized());
    }
}
