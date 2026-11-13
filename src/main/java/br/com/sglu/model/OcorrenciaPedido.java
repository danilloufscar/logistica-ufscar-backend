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
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "pedido_ocorrencias")
public class OcorrenciaPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 500)
    private String descricao;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    protected OcorrenciaPedido() {
        // Required by JPA.
    }

    public OcorrenciaPedido(Pedido pedido, Usuario usuario, String descricao, Instant dataHora) {
        this.pedido = Objects.requireNonNull(pedido);
        this.usuario = Objects.requireNonNull(usuario);
        this.descricao = Objects.requireNonNull(descricao).strip();
        if (this.descricao.isEmpty()) {
            throw new IllegalArgumentException("A descrição da ocorrência é obrigatória");
        }
        this.dataHora = Objects.requireNonNull(dataHora);
    }

    public Long getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getDescricao() {
        return descricao;
    }

    public Instant getDataHora() {
        return dataHora;
    }
}