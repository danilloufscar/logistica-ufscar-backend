package br.com.sglu.service;

import br.com.sglu.dto.pedido.AcompanhamentoPedidoResponse;
import br.com.sglu.dto.pedido.ItemPedidoRequest;
import br.com.sglu.dto.pedido.OcorrenciaPedidoRequest;
import br.com.sglu.dto.pedido.PedidoRequest;
import br.com.sglu.dto.pedido.PedidoResponse;
import br.com.sglu.dto.pedido.PedidoResumoResponse;
import br.com.sglu.dto.pedido.TimelineEventoResponse;
import br.com.sglu.exception.DadosInvalidosException;
import br.com.sglu.exception.OperacaoNaoPermitidaException;
import br.com.sglu.exception.PedidoNaoEncontradoException;
import br.com.sglu.exception.SessaoInvalidaException;
import br.com.sglu.model.Cliente;
import br.com.sglu.model.HistoricoStatusPedido;
import br.com.sglu.model.ItemPedido;
import br.com.sglu.model.OcorrenciaPedido;
import br.com.sglu.model.Pedido;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Produto;
import br.com.sglu.model.StatusPedido;
import br.com.sglu.model.TipoOcorrencia;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.ClienteRepository;
import br.com.sglu.repository.OcorrenciaPedidoRepository;
import br.com.sglu.repository.PedidoRepository;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static br.com.sglu.model.StatusPedido.CANCELADO;
import static br.com.sglu.model.StatusPedido.CRIADO;
import static br.com.sglu.model.StatusPedido.DESPACHADO;
import static br.com.sglu.model.StatusPedido.EM_SEPARACAO;
import static br.com.sglu.model.StatusPedido.EM_TRANSITO;
import static br.com.sglu.model.StatusPedido.ENTREGUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {
    static final Instant AGORA = Instant.parse("2026-11-05T01:00:00Z");
    static final LocalDate HOJE = LocalDate.of(2026, 11, 4);

    @Mock PedidoRepository pedidos;
    @Mock ClienteRepository clientes;
    @Mock ProdutoRepository produtos;
    @Mock UsuarioRepository usuarios;
    @Mock GeradorCodigoConsulta gerador;
    @Mock DespachoEstoqueService despacho;
    @Mock OcorrenciaPedidoRepository ocorrencias;
    PedidoService service;

    final Cliente cliente = comId(new Cliente("Comercial Silva", "(15) 3221-4400", "Rua das Flores, 120"), 1L);
    final Produto caixa = comId(new Produto("CX-M", "Caixa média", 5), 10L);
    final Produto fita = comId(new Produto("FIT-48", "Fita 48 mm", 5), 11L);
    final Usuario usuario = comId(new Usuario("Ana", "ana@example.com", "hash", Perfil.OPERADOR), 7L);

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidos, clientes, produtos, usuarios, gerador, Clock.fixed(AGORA, ZoneOffset.UTC),
                despacho, ocorrencias);
    }

    @Test
    void criaPedidoComStatusCriadoCodigoDeConsultaEHistorico() {
        dadosValidos();
        when(gerador.gerar()).thenReturn("ABCD2345");
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));
        when(pedidos.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resposta = service.criar(request(HOJE, item(10L, 2), item(11L, 1)), 7L);

        assertThat(resposta.status()).isEqualTo(CRIADO);
        assertThat(resposta.codigo()).isEqualTo("ABCD2345");
        assertThat(resposta.cliente().nome()).isEqualTo("Comercial Silva");
        assertThat(resposta.enderecoEntrega()).isEqualTo("Rua de entrega, 10");
        assertThat(resposta.criadoEm()).isEqualTo(AGORA);
        assertThat(resposta.atrasado()).isFalse();
        assertThat(resposta.itens()).extracting(item -> item.produto().codigo(), PedidoResponse.Item::quantidade)
                .containsExactly(tuple("CX-M", 2), tuple("FIT-48", 1));
        assertThat(resposta.historico()).singleElement().satisfies(registro -> {
            assertThat(registro.status()).isEqualTo(CRIADO);
            assertThat(registro.dataHora()).isEqualTo(AGORA);
            assertThat(registro.usuario().nome()).isEqualTo("Ana");
        });
        verify(pedidos).existsByCodigoConsulta("ABCD2345");
    }

    @Test
    void sorteiaOutroCodigoQuandoOGeradoJaExiste() {
        dadosValidos();
        when(gerador.gerar()).thenReturn("AAAA2222", "BBBB3333");
        when(pedidos.existsByCodigoConsulta("AAAA2222")).thenReturn(true);
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));
        when(pedidos.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.criar(request(HOJE, item(10L, 1)), 7L).codigo()).isEqualTo("BBBB3333");
    }

    @Test
    void desisteAposTentativasDeCodigoRepetido() {
        dadosValidos();
        when(gerador.gerar()).thenReturn("AAAA2222");
        when(pedidos.existsByCodigoConsulta("AAAA2222")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(request(HOJE, item(10L, 1)), 7L)).isInstanceOf(OperacaoNaoPermitidaException.class);
        verify(gerador, times(PedidoService.TENTATIVAS_CODIGO)).gerar();
        verify(pedidos, never()).saveAndFlush(any());
    }

    @Test
    void reuneErrosDeClienteProdutoEPrazoSemPersistir() {
        when(clientes.findById(1L)).thenReturn(Optional.empty());
        when(produtos.findAllById(any())).thenReturn(List.of(caixa));
        var dados = request(HOJE.minusDays(1), item(10L, 1), item(99L, 1), item(10L, 3));

        assertThatThrownBy(() -> service.criar(dados, 7L)).isInstanceOfSatisfying(DadosInvalidosException.class,
                erro -> assertThat(erro.getDetalhes()).containsExactly(
                        entry("clienteId", "Cliente não encontrado"),
                        entry("itens[1].produtoId", "Produto não encontrado"),
                        entry("itens[2].produtoId", "Produto repetido no pedido"),
                        entry("prazoEntrega", "O prazo de entrega não pode ser anterior a hoje")));
        verify(gerador, never()).gerar();
        verify(pedidos, never()).saveAndFlush(any());
    }

    @Test
    void listaMaisRecentesPrimeiroEIndicaAtrasoPeloCalendarioDeSaoPaulo() {
        var venceHoje = pedido(HOJE);
        var vencido = pedido(HOJE.minusDays(1));
        var entregue = pedido(HOJE.minusDays(3));
        avancarAte(entregue, ENTREGUE);
        var cancelado = pedido(HOJE.minusDays(3));
        cancelado.cancelar(usuario, AGORA);
        var maisRecentes = Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"));
        when(pedidos.findAll(ArgumentMatchers.<Specification<Pedido>>any(), eq(maisRecentes)))
                .thenReturn(List.of(venceHoje, vencido, entregue, cancelado));

        assertThat(service.listar(CRIADO, 1L)).extracting(PedidoResumoResponse::atrasado)
                .containsExactly(false, true, false, false);
    }

    @Test
    void avancaUmaEtapaPorVezAteEntregueRegistrandoUsuarioEDataHora() {
        var pedido = pedido(HOJE.plusDays(5));
        when(pedidos.buscarParaAtualizar(5L)).thenReturn(Optional.of(pedido));
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));

        var atual = CRIADO;
        for (var proximo : List.of(EM_SEPARACAO, DESPACHADO, EM_TRANSITO, ENTREGUE)) {
            assertThat(service.avancar(5L, atual, 7L).status()).isEqualTo(proximo);
            atual = proximo;
        }

        assertThat(pedido.getHistorico()).extracting(HistoricoStatusPedido::getStatus)
                .containsExactly(CRIADO, EM_SEPARACAO, DESPACHADO, EM_TRANSITO, ENTREGUE);
        assertThat(pedido.getHistorico()).allSatisfy(registro -> {
            assertThat(registro.getUsuario()).isSameAs(usuario);
            assertThat(registro.getDataHora()).isEqualTo(AGORA);
        });
        assertThatThrownBy(() -> service.avancar(5L, ENTREGUE, 7L)).isInstanceOfSatisfying(OperacaoNaoPermitidaException.class,
                erro -> assertThat(erro.getCodigo()).isEqualTo("PEDIDO_SEM_PROXIMO_STATUS"));
    }

    @Test
    void recusaAvancoComStatusDesatualizadoSemPularEtapa() {
        var pedido = pedido(HOJE.plusDays(5));
        when(pedidos.buscarParaAtualizar(5L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> service.avancar(5L, EM_SEPARACAO, 7L)).isInstanceOfSatisfying(OperacaoNaoPermitidaException.class,
                erro -> assertThat(erro.getCodigo()).isEqualTo("STATUS_DESATUALIZADO"));
        assertThat(pedido.getStatus()).isEqualTo(CRIADO);
        verifyNoInteractions(usuarios);
    }

    @Test
    void editaPedidoEmSeparacaoAtualizandoItensNoLugar() {
        var pedido = pedido(HOJE.plusDays(5));
        pedido.avancar(usuario, AGORA);
        var itemCaixa = pedido.getItens().getFirst();
        when(pedidos.buscarParaAtualizar(5L)).thenReturn(Optional.of(pedido));
        dadosValidos();

        var resposta = service.editar(5L, request(HOJE.plusDays(8), item(11L, 4), item(10L, 3)));

        assertThat(resposta.status()).isEqualTo(EM_SEPARACAO);
        assertThat(resposta.prazoEntrega()).isEqualTo(HOJE.plusDays(8));
        assertThat(resposta.itens()).extracting(item -> item.produto().codigo(), PedidoResponse.Item::quantidade)
                .containsExactlyInAnyOrder(tuple("CX-M", 3), tuple("FIT-48", 4));
        assertThat(pedido.getItens()).contains(itemCaixa);
        assertThat(itemCaixa.getQuantidade()).isEqualTo(3);

        service.editar(5L, request(HOJE.plusDays(8), item(11L, 1)));
        assertThat(pedido.getItens()).extracting(item -> item.getProduto().getCodigo()).containsExactly("FIT-48");
    }

    @Test
    void mantemPrazoVencidoNaEdicaoMasRecusaNovoPrazoNoPassado() {
        var pedido = pedido(HOJE.minusDays(2));
        when(pedidos.buscarParaAtualizar(5L)).thenReturn(Optional.of(pedido));
        dadosValidos();

        assertThat(service.editar(5L, request(HOJE.minusDays(2), item(10L, 1))).atrasado()).isTrue();
        assertThatThrownBy(() -> service.editar(5L, request(HOJE.minusDays(1), item(10L, 1))))
                .isInstanceOfSatisfying(DadosInvalidosException.class,
                        erro -> assertThat(erro.getDetalhes()).containsOnlyKeys("prazoEntrega"));
    }

    @Test
    void naoEditaNemCancelaDepoisDoDespacho() {
        var pedido = pedido(HOJE.plusDays(5));
        avancarAte(pedido, DESPACHADO);
        when(pedidos.buscarParaAtualizar(5L)).thenReturn(Optional.of(pedido));
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));
        dadosValidos();

        assertThatThrownBy(() -> service.editar(5L, request(HOJE.plusDays(5), item(10L, 9))))
                .isInstanceOfSatisfying(OperacaoNaoPermitidaException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("PEDIDO_NAO_EDITAVEL"));
        assertThatThrownBy(() -> service.cancelar(5L, 7L)).isInstanceOfSatisfying(OperacaoNaoPermitidaException.class,
                erro -> assertThat(erro.getCodigo()).isEqualTo("PEDIDO_NAO_CANCELAVEL"));
        assertThat(pedido.getStatus()).isEqualTo(DESPACHADO);
        assertThat(pedido.getItens()).singleElement().extracting(ItemPedido::getQuantidade).isEqualTo(2);
    }

    @Test
    void cancelaPedidoCriadoECanceladoEFinal() {
        var pedido = pedido(HOJE.minusDays(1));
        when(pedidos.buscarParaAtualizar(5L)).thenReturn(Optional.of(pedido));
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));

        var resposta = service.cancelar(5L, 7L);

        assertThat(resposta.status()).isEqualTo(CANCELADO);
        assertThat(resposta.atrasado()).isFalse();
        assertThat(resposta.historico()).extracting(PedidoResponse.Historico::status).containsExactly(CRIADO, CANCELADO);
        assertThatThrownBy(() -> service.avancar(5L, CANCELADO, 7L)).isInstanceOfSatisfying(OperacaoNaoPermitidaException.class,
                erro -> assertThat(erro.getCodigo()).isEqualTo("PEDIDO_SEM_PROXIMO_STATUS"));
        assertThatThrownBy(() -> service.cancelar(5L, 7L)).isInstanceOfSatisfying(OperacaoNaoPermitidaException.class,
                erro -> assertThat(erro.getCodigo()).isEqualTo("PEDIDO_NAO_CANCELAVEL"));
    }

    @Test
    void detalhaPedidoEExigePedidoEUsuarioExistentes() {
        var pedido = pedido(HOJE);
        when(pedidos.buscarParaAtualizar(5L)).thenReturn(Optional.of(pedido));
        when(pedidos.findById(5L)).thenReturn(Optional.of(pedido));
        when(pedidos.findById(99L)).thenReturn(Optional.empty());
        when(usuarios.findById(7L)).thenReturn(Optional.empty());

        assertThat(service.detalhar(5L).codigo()).isEqualTo("ABCD2345");
        assertThatThrownBy(() -> service.detalhar(99L)).isInstanceOf(PedidoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.cancelar(5L, 7L)).isInstanceOf(SessaoInvalidaException.class);
        assertThat(pedido.getStatus()).isEqualTo(CRIADO);
    }

    // RF13: linha do tempo

    @Test
    void timelineIntercalaStatusEOcorrenciasEmOrdemCronologica() {
        var pedido = comId(pedido(HOJE.plusDays(2)), 5L);
        pedido.avancar(usuario, AGORA.plusSeconds(120));
        var ocorrencia = new OcorrenciaPedido(pedido, usuario, TipoOcorrencia.AVARIA, "Caixa amassada", HOJE, AGORA.plusSeconds(60));
        when(pedidos.findById(5L)).thenReturn(Optional.of(pedido));
        when(ocorrencias.buscarPorPedidoId(5L)).thenReturn(List.of(ocorrencia));

        var eventos = service.timeline(5L);

        assertThat(eventos).extracting(TimelineEventoResponse::tipo, TimelineEventoResponse::status, TimelineEventoResponse::tipoOcorrencia)
                .containsExactly(
                        tuple(TimelineEventoResponse.TipoEvento.STATUS, CRIADO, null),
                        tuple(TimelineEventoResponse.TipoEvento.OCORRENCIA, null, TipoOcorrencia.AVARIA),
                        tuple(TimelineEventoResponse.TipoEvento.STATUS, EM_SEPARACAO, null));
        assertThat(eventos.get(1).descricao()).isEqualTo("Caixa amassada");
        assertThat(eventos.get(1).usuario().nome()).isEqualTo("Ana");
        assertThat(eventos.get(2).dataHora()).isEqualTo(AGORA.plusSeconds(120));
    }

    @Test
    void timelineDePedidoInexistenteNaoConsultaOcorrencias() {
        when(pedidos.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.timeline(99L)).isInstanceOf(PedidoNaoEncontradoException.class);
        verifyNoInteractions(ocorrencias);
    }

    // RF14: ocorrências

    @Test
    void registraOcorrenciaComTipoDataEAutorSemMudarOStatus() {
        var pedido = comId(pedido(HOJE.plusDays(2)), 5L);
        avancarAte(pedido, EM_TRANSITO);
        when(pedidos.buscarParaAtualizar(5L)).thenReturn(Optional.of(pedido));
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));
        when(ocorrencias.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resposta = service.registrarOcorrencia(5L,
                new OcorrenciaPedidoRequest(TipoOcorrencia.DESTINATARIO_AUSENTE, "  Ninguém no local  ", HOJE), 7L);

        assertThat(resposta.tipo()).isEqualTo(TimelineEventoResponse.TipoEvento.OCORRENCIA);
        assertThat(resposta.tipoOcorrencia()).isEqualTo(TipoOcorrencia.DESTINATARIO_AUSENTE);
        assertThat(resposta.descricao()).isEqualTo("Ninguém no local");
        assertThat(resposta.dataOcorrencia()).isEqualTo(HOJE);
        assertThat(resposta.dataHora()).isEqualTo(AGORA);
        assertThat(resposta.usuario().id()).isEqualTo(7L);
        assertThat(pedido.getStatus()).isEqualTo(EM_TRANSITO);
        assertThat(pedido.getHistorico()).hasSize(4);
    }

    @Test
    void recusaOcorrenciaEmPedidoEncerradoSemGravar() {
        var pedido = comId(pedido(HOJE.plusDays(2)), 5L);
        pedido.cancelar(usuario, AGORA);
        when(pedidos.buscarParaAtualizar(5L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> service.registrarOcorrencia(5L,
                new OcorrenciaPedidoRequest(TipoOcorrencia.ATRASO, "Tarde demais", HOJE), 7L))
                .isInstanceOfSatisfying(OperacaoNaoPermitidaException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("PEDIDO_ENCERRADO"));
        verifyNoInteractions(ocorrencias, usuarios);
    }

    // RF17: consulta pública

    @Test
    void consultaPublicaTrazSoStatusPrazoEMudancasDeStatus() {
        var pedido = comId(pedido(HOJE.plusDays(3)), 5L);
        pedido.avancar(usuario, AGORA.plusSeconds(60));
        when(pedidos.findByCodigoConsultaIgnoreCase("ABCD2345")).thenReturn(Optional.of(pedido));

        var resposta = service.acompanharPorCodigo(" ABCD2345 ");

        assertThat(resposta.codigo()).isEqualTo("ABCD2345");
        assertThat(resposta.status()).isEqualTo(EM_SEPARACAO);
        assertThat(resposta.statusRotulo()).isEqualTo("Em separação");
        assertThat(resposta.prazoEntrega()).isEqualTo(HOJE.plusDays(3));
        assertThat(resposta.atualizadoEm()).isEqualTo(AGORA);
        assertThat(resposta.intervaloAtualizacaoSegundos()).isEqualTo(30);
        assertThat(resposta.timeline())
                .extracting(AcompanhamentoPedidoResponse.Etapa::status, AcompanhamentoPedidoResponse.Etapa::dataHora)
                .containsExactly(tuple(CRIADO, AGORA), tuple(EM_SEPARACAO, AGORA.plusSeconds(60)));
        verifyNoInteractions(ocorrencias);
    }

    @Test
    void consultaPublicaComCodigoVazioOuInexistenteRetornaNaoEncontrado() {
        when(pedidos.findByCodigoConsultaIgnoreCase("ZZZZ9999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.acompanharPorCodigo(" ")).isInstanceOf(PedidoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.acompanharPorCodigo(null)).isInstanceOf(PedidoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.acompanharPorCodigo("ZZZZ9999")).isInstanceOf(PedidoNaoEncontradoException.class);
    }

    private void dadosValidos() {
        when(clientes.findById(1L)).thenReturn(Optional.of(cliente));
        when(produtos.findAllById(any())).thenReturn(List.of(caixa, fita));
    }

    private Pedido pedido(LocalDate prazo) {
        return new Pedido("ABCD2345", cliente, "Rua de entrega, 10", prazo, List.of(new Pedido.Item(caixa, 2)), usuario, AGORA);
    }

    private void avancarAte(Pedido pedido, StatusPedido alvo) {
        while (pedido.getStatus() != alvo) pedido.avancar(usuario, AGORA);
    }

    private static PedidoRequest request(LocalDate prazo, ItemPedidoRequest... itens) {
        return new PedidoRequest(1L, " Rua de entrega, 10 ", prazo, List.of(itens));
    }

    private static ItemPedidoRequest item(long produtoId, int quantidade) {
        return new ItemPedidoRequest(produtoId, quantidade);
    }

    private static <T> T comId(T entidade, long id) {
        ReflectionTestUtils.setField(entidade, "id", id);
        return entidade;
    }
    @Test
    void detalheInternoDestacaVencidoMasNaoHojeFuturoOuEncerrados() {
        var hoje = pedido(HOJE);
        var futuro = pedido(HOJE.plusDays(1));
        var vencido = pedido(HOJE.minusDays(1));
        var entregue = pedido(HOJE.minusDays(1));
        avancarAte(entregue, ENTREGUE);
        var cancelado = pedido(HOJE.minusDays(1));
        cancelado.cancelar(usuario, AGORA);
        assertThat(List.of(hoje, futuro, vencido, entregue, cancelado).stream()
                .map(p -> PedidoResponse.from(p, HOJE).atrasado()))
                .containsExactly(false, false, true, false, false);
    }}
