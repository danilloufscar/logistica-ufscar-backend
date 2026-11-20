package br.com.sglu.dto.estoque;

import br.com.sglu.model.MovimentacaoEstoque;
import br.com.sglu.model.TipoMovimentacaoEstoque;
import java.time.Instant;
import java.time.LocalDate;

public record EntradaEstoqueResponse(Long id, ProdutoResumo produto, TipoMovimentacaoEstoque tipo,
                                    Integer quantidade, LocalDate data, Instant registradoEm,
                                    UsuarioResumo usuario, Integer saldoAtual) {
    public record ProdutoResumo(Long id, String codigo, String nome) {}
    public record UsuarioResumo(Long id, String nome) {}

    public static EntradaEstoqueResponse from(MovimentacaoEstoque entrada) {
        var produto = entrada.getProduto();
        var usuario = entrada.getUsuario();
        return new EntradaEstoqueResponse(entrada.getId(), new ProdutoResumo(produto.getId(), produto.getCodigo(), produto.getNome()),
                entrada.getTipo(), entrada.getQuantidade(), entrada.getData(), entrada.getRegistradoEm(),
                new UsuarioResumo(usuario.getId(), usuario.getNome()), produto.getSaldo());
    }
}
