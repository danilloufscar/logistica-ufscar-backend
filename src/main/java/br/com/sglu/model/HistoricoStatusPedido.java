
package br.com.sglu.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "pedido_historico")
public class HistoricoStatusPedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, updatable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StatusPedido status;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    protected HistoricoStatusPedido() {
        // Required by JPA.
    }

    HistoricoStatusPedido(Pedido pedido, StatusPedido status, Usuario usuario, Instant dataHora) {
        this.pedido = Objects.requireNonNull(pedido);
        this.status = Objects.requireNonNull(status);
        this.usuario = Objects.requireNonNull(usuario);
        this.dataHora = Objects.requireNonNull(dataHora);
    }

    public Long getId() { return id; }
    public StatusPedido getStatus() { return status; }
    public Instant getDataHora() { return dataHora; }
    public Usuario getUsuario() { return usuario; }
}
