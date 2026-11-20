package br.com.sglu.service;

import br.com.sglu.exception.EstoqueInsuficienteException;
import br.com.sglu.model.ItemPedido;
import br.com.sglu.model.MovimentacaoEstoque;
import br.com.sglu.model.Pedido;
import br.com.sglu.model.Produto;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.MovimentacaoEstoqueRepository;
import br.com.sglu.repository.ProdutoRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DespachoEstoqueService {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private final ProdutoRepository produtos;
    private final MovimentacaoEstoqueRepository movimentacoes;

    public DespachoEstoqueService(ProdutoRepository produtos, MovimentacaoEstoqueRepository movimentacoes) {
        this.produtos = produtos;
        this.movimentacoes = movimentacoes;
    }

    private record Baixa(Produto produto, int quantidade) {}

    /** Participa da mesma transação do pedido; nunca efetiva uma baixa isoladamente. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void baixar(Pedido pedido, Usuario usuario, Instant agora) {
        var baixas = new ArrayList<Baixa>();
        var faltas = new ArrayList<EstoqueInsuficienteException.Item>();
        // A ordem global de locks evita deadlocks entre pedidos com produtos em ordens diferentes.
        var itens = pedido.getItens().stream()
                .sorted(Comparator.comparing(item -> item.getProduto().getId())).toList();
        for (ItemPedido item : itens) {
            var produto = produtos.buscarParaAtualizarEstoque(item.getProduto().getId()).orElseThrow();
            baixas.add(new Baixa(produto, item.getQuantidade()));
            if (produto.getSaldo() < item.getQuantidade()) {
                faltas.add(EstoqueInsuficienteException.Item.from(produto, item.getQuantidade()));
            }
        }
        if (!faltas.isEmpty()) throw new EstoqueInsuficienteException(faltas);

        var data = agora.atZone(FUSO).toLocalDate();
        var saidas = new ArrayList<MovimentacaoEstoque>();
        for (Baixa baixa : baixas) {
            baixa.produto().registrarSaida(baixa.quantidade());
            saidas.add(MovimentacaoEstoque.saida(baixa.produto(), baixa.quantidade(), data, usuario, agora, pedido));
        }
        movimentacoes.saveAllAndFlush(saidas);
    }
}
