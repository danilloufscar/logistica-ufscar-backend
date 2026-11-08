package br.com.sglu.model;

import br.com.sglu.exception.OperacaoNaoPermitidaException;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "pedidos")
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_consulta", nullable = false, unique = true, length = 8, updatable = false)
    private String codigoConsulta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "endereco_entrega", nullable = false, length = 500)
    private String enderecoEntrega;

    @Column(name = "prazo_entrega", nullable = false)
    private LocalDate prazoEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPedido status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Version
    @Column(nullable = false)
    private Long versao;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemPedido> itens = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    @OrderBy("dataHora, id")
    private List<HistoricoStatusPedido> historico = new ArrayList<>();

    public record Item(Produto produto, int quantidade) {}

    protected Pedido() {
        // Required by JPA.
    }

    public Pedido(String codigoConsulta, Cliente cliente, String enderecoEntrega, LocalDate prazoEntrega,
                  List<Item> itens, Usuario usuario, Instant agora) {
        this.codigoConsulta = Objects.requireNonNull(codigoConsulta);
        this.criadoEm = Objects.requireNonNull(agora);
        this.status = StatusPedido.CRIADO;
        definirDados(cliente, enderecoEntrega, prazoEntrega, itens);
        registrarStatus(usuario, agora);
    }

    public void editar(Cliente cliente, String enderecoEntrega, LocalDate prazoEntrega, List<Item> itens) {
        exigirAlteravel("PEDIDO_NAO_EDITAVEL", "editado");
        definirDados(cliente, enderecoEntrega, prazoEntrega, itens);
    }

    public void cancelar(Usuario usuario, Instant agora) {
        exigirAlteravel("PEDIDO_NAO_CANCELAVEL", "cancelado");
        status = StatusPedido.CANCELADO;
        registrarStatus(usuario, agora);
    }

    public void avancar(Usuario usuario, Instant agora) {
        status = status.proximo().orElseThrow(() -> new OperacaoNaoPermitidaException("PEDIDO_SEM_PROXIMO_STATUS",
                "Pedido com status " + status.getRotulo() + " não pode avançar"));
        registrarStatus(usuario, agora);
    }

    public boolean isAtrasado(LocalDate hoje) {
        return !status.isEncerrado() && hoje.isAfter(prazoEntrega);
    }

    private void exigirAlteravel(String codigo, String acao) {
        if (!status.permiteAlteracao()) {
            throw new OperacaoNaoPermitidaException(codigo, "Pedido com status " + status.getRotulo()
                    + " não pode ser " + acao + ". Edição e cancelamento só são permitidos em Criado ou Em separação");
        }
    }

    private void definirDados(Cliente cliente, String enderecoEntrega, LocalDate prazoEntrega, List<Item> novosItens) {
        this.cliente = Objects.requireNonNull(cliente);
        this.enderecoEntrega = Objects.requireNonNull(enderecoEntrega).strip();
        this.prazoEntrega = Objects.requireNonNull(prazoEntrega);
        substituirItens(novosItens);
    }

    private void substituirItens(List<Item> novos) {
        if (novos.isEmpty()) throw new IllegalArgumentException("O pedido deve ter ao menos um item");
        var porProduto = new LinkedHashMap<Long, Item>();
        for (Item item : novos) {
            if (porProduto.putIfAbsent(item.produto().getId(), item) != null) {
                throw new IllegalArgumentException("Produto repetido no pedido");
            }
        }
        itens.removeIf(atual -> !porProduto.containsKey(atual.getProduto().getId()));
        for (Item novo : porProduto.values()) {
            itens.stream()
                    .filter(atual -> Objects.equals(atual.getProduto().getId(), novo.produto().getId()))
                    .findFirst()
                    .ifPresentOrElse(atual -> atual.alterarQuantidade(novo.quantidade()),
                            () -> itens.add(new ItemPedido(this, novo.produto(), novo.quantidade())));
        }
    }

    private void registrarStatus(Usuario usuario, Instant agora) {
        historico.add(new HistoricoStatusPedido(this, status, usuario, agora));
    }

    public Long getId() { return id; }
    public String getCodigoConsulta() { return codigoConsulta; }
    public Cliente getCliente() { return cliente; }
    public String getEnderecoEntrega() { return enderecoEntrega; }
    public LocalDate getPrazoEntrega() { return prazoEntrega; }
    public StatusPedido getStatus() { return status; }
    public Instant getCriadoEm() { return criadoEm; }
    public List<ItemPedido> getItens() { return Collections.unmodifiableList(itens); }
    public List<HistoricoStatusPedido> getHistorico() { return Collections.unmodifiableList(historico); }
}

