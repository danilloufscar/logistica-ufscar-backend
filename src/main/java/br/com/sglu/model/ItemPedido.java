
package br.com.sglu.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "pedido_itens")
public class ItemPedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, updatable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidade;

    protected ItemPedido() {
        // Required by JPA.
    }

    ItemPedido(Pedido pedido, Produto produto, int quantidade) {
        this.pedido = Objects.requireNonNull(pedido);
        this.produto = Objects.requireNonNull(produto);
        alterarQuantidade(quantidade);
    }

    void alterarQuantidade(int quantidade) {
        if (quantidade <= 0) throw new IllegalArgumentException("A quantidade deve ser maior que zero");
        this.quantidade = quantidade;
    }

    public Long getId() { return id; }
    public Produto getProduto() { return produto; }
    public Integer getQuantidade() { return quantidade; }
}
