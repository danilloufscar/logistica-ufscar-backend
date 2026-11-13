package br.com.sglu.exception;

import br.com.sglu.model.Produto;
import java.util.List;

public class EstoqueInsuficienteException extends RuntimeException {
    public record Item(Long produtoId, String codigo, String nome, int solicitado, int disponivel, int faltante) {
        public static Item from(Produto produto, int solicitado) {
            return new Item(produto.getId(), produto.getCodigo(), produto.getNome(), solicitado,
                    produto.getSaldo(), solicitado - produto.getSaldo());
        }
    }

    private final List<Item> itens;

    public EstoqueInsuficienteException(List<Item> itens) {
        super("Despacho bloqueado: há produtos com estoque insuficiente. Nenhum item foi baixado");
        this.itens = List.copyOf(itens);
    }

    public List<Item> getItens() { return itens; }
}
