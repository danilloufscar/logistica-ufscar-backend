package br.com.sglu.service;

import br.com.sglu.dto.pedido.ItemPedidoRequest;
import br.com.sglu.dto.pedido.PedidoRequest;
import br.com.sglu.dto.pedido.PedidoResponse;
import br.com.sglu.dto.pedido.PedidoResumoResponse;
import br.com.sglu.exception.DadosInvalidosException;
import br.com.sglu.exception.OperacaoNaoPermitidaException;
import br.com.sglu.exception.PedidoNaoEncontradoException;
import br.com.sglu.exception.SessaoInvalidaException;
import br.com.sglu.model.Cliente;
import br.com.sglu.model.Pedido;
import br.com.sglu.model.Produto;
import br.com.sglu.model.StatusPedido;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.ClienteRepository;
import br.com.sglu.repository.PedidoRepository;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    static final int TENTATIVAS_CODIGO = 5;
    private static final Sort MAIS_RECENTES = Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"));

    private final PedidoRepository pedidos;
    private final ClienteRepository clientes;
    private final ProdutoRepository produtos;
    private final UsuarioRepository usuarios;
    private final GeradorCodigoConsulta gerador;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidos, ClienteRepository clientes, ProdutoRepository produtos,
                         UsuarioRepository usuarios, GeradorCodigoConsulta gerador, Clock clock) {
        this.pedidos = pedidos;
        this.clientes = clientes;
        this.produtos = produtos;
        this.usuarios = usuarios;
        this.gerador = gerador;
        this.clock = clock;
    }

    @Transactional
    public PedidoResponse criar(PedidoRequest dados, Long usuarioId) {
        var hoje = hoje();
        var validados = validar(dados, null, hoje);
        var pedido = new Pedido(gerarCodigo(), validados.cliente(), dados.enderecoEntrega(), dados.prazoEntrega(),
                validados.itens(), usuario(usuarioId), clock.instant());
        return PedidoResponse.from(pedidos.saveAndFlush(pedido), hoje);
    }

    @Transactional(readOnly = true)
    public List<PedidoResumoResponse> listar(StatusPedido status, Long clienteId) {
        var hoje = hoje();
        return pedidos.findAll(PedidoRepository.filtro(status, clienteId), MAIS_RECENTES).stream()
                .map(pedido -> PedidoResumoResponse.from(pedido, hoje))
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse detalhar(Long id) {
        return PedidoResponse.from(buscar(id), hoje());
    }

    @Transactional
    public PedidoResponse editar(Long id, PedidoRequest dados) {
        var hoje = hoje();
        var pedido = buscar(id);
        var validados = validar(dados, pedido.getPrazoEntrega(), hoje);
        pedido.editar(validados.cliente(), dados.enderecoEntrega(), dados.prazoEntrega(), validados.itens());
        return PedidoResponse.from(pedido, hoje);
    }

    @Transactional
    public PedidoResponse cancelar(Long id, Long usuarioId) {
        var pedido = buscar(id);
        pedido.cancelar(usuario(usuarioId), clock.instant());
        return PedidoResponse.from(pedido, hoje());
    }

    @Transactional
    public PedidoResponse avancar(Long id, StatusPedido statusAtual, Long usuarioId) {
        var pedido = buscar(id);
        if (pedido.getStatus() != statusAtual) {
            throw new OperacaoNaoPermitidaException("STATUS_DESATUALIZADO", "O pedido já está com status "
                    + pedido.getStatus().getRotulo() + ". Atualize a página e tente novamente");
        }
        pedido.avancar(usuario(usuarioId), clock.instant());
        return PedidoResponse.from(pedido, hoje());
    }

    private record DadosValidados(Cliente cliente, List<Pedido.Item> itens) {}

    private DadosValidados validar(PedidoRequest dados, LocalDate prazoAtual, LocalDate hoje) {
        var erros = new LinkedHashMap<String, String>();
        var cliente = clientes.findById(dados.clienteId()).orElse(null);
        if (cliente == null) erros.put("clienteId", "Cliente não encontrado");

        var ids = dados.itens().stream().map(ItemPedidoRequest::produtoId).collect(Collectors.toSet());
        Map<Long, Produto> encontrados = produtos.findAllById(ids).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));
        var itens = new ArrayList<Pedido.Item>();
        var usados = new HashSet<Long>();
        for (int i = 0; i < dados.itens().size(); i++) {
            var item = dados.itens().get(i);
            var produto = encontrados.get(item.produtoId());
            if (produto == null) erros.put("itens[" + i + "].produtoId", "Produto não encontrado");
            else if (!usados.add(item.produtoId())) erros.put("itens[" + i + "].produtoId", "Produto repetido no pedido");
            else itens.add(new Pedido.Item(produto, item.quantidade()));
        }

        boolean prazoAlterado = !dados.prazoEntrega().equals(prazoAtual);
        if (prazoAlterado && dados.prazoEntrega().isBefore(hoje)) {
            erros.put("prazoEntrega", "O prazo de entrega não pode ser anterior a hoje");
        }
        if (!erros.isEmpty()) throw new DadosInvalidosException(erros);
        return new DadosValidados(cliente, itens);
    }

    private String gerarCodigo() {
        for (int tentativa = 0; tentativa < TENTATIVAS_CODIGO; tentativa++) {
            var codigo = gerador.gerar();
            if (!pedidos.existsByCodigoConsulta(codigo)) return codigo;
        }
        throw new OperacaoNaoPermitidaException("CODIGO_CONSULTA_INDISPONIVEL",
                "Não foi possível reservar um código de consulta. Tente novamente");
    }

    private Pedido buscar(Long id) {
        return pedidos.findById(id).orElseThrow(PedidoNaoEncontradoException::new);
    }

    private Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow(SessaoInvalidaException::new);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO));
    }
}
