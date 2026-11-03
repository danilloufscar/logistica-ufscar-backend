package br.com.sglu.dto.produto;

import br.com.sglu.model.Produto;

public record ProdutoResponse(Long id, String codigo, String nome, Integer estoqueMinimo) {
    public static ProdutoResponse from(Produto produto) {
        return new ProdutoResponse(produto.getId(), produto.getCodigo(), produto.getNome(), produto.getEstoqueMinimo());
    }
}
