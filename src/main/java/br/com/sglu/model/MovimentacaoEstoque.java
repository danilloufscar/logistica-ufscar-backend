package br.com.sglu.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "movimentacoes_estoque")
public class MovimentacaoEstoque {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TipoMovimentacaoEstoque tipo;

    @Column(nullable = false, updatable = false)
    private Integer quantidade;

    @Column(nullable = false, updatable = false)
    private LocalDate data;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private Instant registradoEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    protected MovimentacaoEstoque() {}

    public static MovimentacaoEstoque entrada(Produto produto, int quantidade, LocalDate data, Usuario usuario, Instant agora) {
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser maior que zero");
        var movimentacao = new MovimentacaoEstoque();
        movimentacao.produto = Objects.requireNonNull(produto);
        movimentacao.tipo = TipoMovimentacaoEstoque.ENTRADA;
        movimentacao.quantidade = quantidade;
        movimentacao.data = Objects.requireNonNull(data);
        movimentacao.usuario = Objects.requireNonNull(usuario);
        movimentacao.registradoEm = Objects.requireNonNull(agora);
        return movimentacao;
    }

    public Long getId() { return id; }
    public Produto getProduto() { return produto; }
    public TipoMovimentacaoEstoque getTipo() { return tipo; }
    public Integer getQuantidade() { return quantidade; }
    public LocalDate getData() { return data; }
    public Instant getRegistradoEm() { return registradoEm; }
    public Usuario getUsuario() { return usuario; }
}
