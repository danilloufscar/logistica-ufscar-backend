package br.com.sglu.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;

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
}
