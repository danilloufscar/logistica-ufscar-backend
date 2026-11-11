package br.com.sglu.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import br.com.sglu.exception.OperacaoNaoPermitidaException;

@Entity
@Table(name = "produtos")
public class Produto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "estoque_minimo", nullable = false)
    private Integer estoqueMinimo;

    @Column(nullable = false)
    private Integer saldo = 0;

    protected Produto() {
        // Required by JPA.
    }

    public Produto(String codigo, String nome, Integer estoqueMinimo) {
        this.codigo = normalizarCodigo(codigo);
        this.nome = Objects.requireNonNull(nome).strip();
        this.estoqueMinimo = Objects.requireNonNull(estoqueMinimo);
        if (estoqueMinimo < 0) throw new IllegalArgumentException("Estoque mínimo não pode ser negativo");
    }

    public static String normalizarCodigo(String codigo) {
        return Objects.requireNonNull(codigo).strip().toUpperCase(Locale.ROOT);
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public Integer getEstoqueMinimo() { return estoqueMinimo; }
    public Integer getSaldo() { return saldo; }

    public boolean estaNoOuAbaixoDoMinimo() {
        return saldo <= estoqueMinimo;
    }

    public void registrarEntrada(int quantidade) {
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser maior que zero");
        try {
            saldo = Math.addExact(saldo, quantidade);
        } catch (ArithmeticException exception) {
            throw new OperacaoNaoPermitidaException("SALDO_LIMITE_EXCEDIDO",
                    "A entrada ultrapassa o limite de 2.147.483.647 unidades do produto");
        }
    }
}
