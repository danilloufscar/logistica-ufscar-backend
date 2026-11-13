package br.com.sglu.dto.estoque;

import br.com.sglu.model.Produto;

public record SaldoEstoqueResponse(Long id, String codigo, String nome, Integer saldo,
                                  Integer estoqueMinimo, boolean abaixoOuNoMinimo) {
    public static SaldoEstoqueResponse from(Produto produto) {
        return new SaldoEstoqueResponse(produto.getId(), produto.getCodigo(), produto.getNome(),
                produto.getSaldo(), produto.getEstoqueMinimo(), produto.estaNoOuAbaixoDoMinimo());
    }
}
