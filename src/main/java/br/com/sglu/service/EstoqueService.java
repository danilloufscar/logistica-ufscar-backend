package br.com.sglu.service;

import br.com.sglu.dto.estoque.EntradaEstoqueRequest;
import br.com.sglu.dto.estoque.EntradaEstoqueResponse;
import br.com.sglu.exception.DadosInvalidosException;
import br.com.sglu.exception.SessaoInvalidaException;
import br.com.sglu.model.MovimentacaoEstoque;
import br.com.sglu.repository.MovimentacaoEstoqueRepository;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstoqueService {
    private final ProdutoRepository produtos;
    private final MovimentacaoEstoqueRepository movimentacoes;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public EstoqueService(ProdutoRepository produtos, MovimentacaoEstoqueRepository movimentacoes,
                         UsuarioRepository usuarios, Clock clock) {
        this.produtos = produtos;
        this.movimentacoes = movimentacoes;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional
    public EntradaEstoqueResponse registrarEntrada(EntradaEstoqueRequest dados, Long usuarioId) {
        var usuario = usuarios.findById(usuarioId).orElseThrow(SessaoInvalidaException::new);
        // O bloqueio dura até o commit e serializa alterações de saldo do mesmo produto.
        var produto = produtos.buscarParaAtualizarEstoque(dados.produtoId())
                .orElseThrow(() -> new DadosInvalidosException(Map.of("produtoId", "Produto não encontrado")));
        produto.registrarEntrada(dados.quantidade());
        var entrada = MovimentacaoEstoque.entrada(produto, dados.quantidade(), dados.data(), usuario, clock.instant());
        return EntradaEstoqueResponse.from(movimentacoes.saveAndFlush(entrada));
    }
}
