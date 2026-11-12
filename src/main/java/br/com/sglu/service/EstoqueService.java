package br.com.sglu.service;

import br.com.sglu.dto.estoque.AjusteEstoqueRequest;
import br.com.sglu.dto.estoque.AjusteEstoqueResponse;
import br.com.sglu.dto.estoque.EntradaEstoqueRequest;
import br.com.sglu.dto.estoque.EntradaEstoqueResponse;
import br.com.sglu.dto.estoque.SaldoEstoqueResponse;
import br.com.sglu.exception.DadosInvalidosException;
import br.com.sglu.exception.SessaoInvalidaException;
import br.com.sglu.model.MovimentacaoEstoque;
import br.com.sglu.model.TipoMovimentacaoEstoque;
import br.com.sglu.repository.MovimentacaoEstoqueRepository;
import br.com.sglu.repository.ProdutoRepository;
import br.com.sglu.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import org.springframework.data.domain.Sort;
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

    @Transactional(readOnly = true)
    public List<SaldoEstoqueResponse> listarSaldos() {
        return produtos.findAll(Sort.by("nome", "id")).stream()
                .map(SaldoEstoqueResponse::from)
                .toList();
    }

    @Transactional
    public EntradaEstoqueResponse registrarEntrada(EntradaEstoqueRequest dados, Long usuarioId) {
        var usuario = usuarios.findById(usuarioId).orElseThrow(SessaoInvalidaException::new);
        // O bloqueio dura até o commit e serializa alterações de saldo do mesmo
        // produto.
        var produto = produtos.buscarParaAtualizarEstoque(dados.produtoId())
                .orElseThrow(() -> new DadosInvalidosException(Map.of("produtoId", "Produto não encontrado")));
        produto.registrarEntrada(dados.quantidade());
        var entrada = MovimentacaoEstoque.entrada(produto, dados.quantidade(), dados.data(), usuario, clock.instant());
        return EntradaEstoqueResponse.from(movimentacoes.saveAndFlush(entrada));
    }

    @Transactional
    public AjusteEstoqueResponse ajustarInventario(AjusteEstoqueRequest dados, Long usuarioId) {
        var usuario = usuarios.findById(usuarioId).orElseThrow(SessaoInvalidaException::new);

        // Bloqueio pessimista FOR UPDATE idêntico ao RF08/RF09
        var produto = produtos.buscarParaAtualizarEstoque(dados.produtoId())
                .orElseThrow(() -> new DadosInvalidosException(Map.of("produtoId", "Produto não encontrado")));

        int saldoAnterior = produto.getSaldo();
        int quantidadeContada = dados.quantidadeContada();
        int diferenca = quantidadeContada - saldoAnterior;

        // Se o saldo contado for exatamente igual ao saldo atual, não há ajuste a
        // registrar
        if (diferenca == 0) {
            throw new DadosInvalidosException(
                    Map.of("quantidadeContada", "A quantidade contada é igual ao saldo atual"));
        }

        TipoMovimentacaoEstoque tipo = diferenca > 0
                ? TipoMovimentacaoEstoque.AJUSTE_ENTRADA
                : TipoMovimentacaoEstoque.AJUSTE_SAIDA;
        int quantidadeAjuste = Math.abs(diferenca);

        produto.ajustarSaldo(quantidadeContada);

        var movimentacao = MovimentacaoEstoque.ajuste(
                produto,
                tipo,
                quantidadeAjuste,
                LocalDate.now(clock),
                usuario,
                clock.instant(),
                dados.justificativa().trim());

        var salva = movimentacoes.saveAndFlush(movimentacao);
        return AjusteEstoqueResponse.from(salva, saldoAnterior, quantidadeContada);
    }
}
