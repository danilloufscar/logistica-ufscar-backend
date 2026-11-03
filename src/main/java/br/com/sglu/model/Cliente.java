package br.com.sglu.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "clientes")
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 30)
    private String telefone;

    @Column(nullable = false, length = 500)
    private String endereco;

    protected Cliente() {
        // Required by JPA.
    }

    public Cliente(String nome, String telefone, String endereco) {
        this.nome = Objects.requireNonNull(nome).strip();
        this.telefone = Objects.requireNonNull(telefone).strip();
        this.endereco = Objects.requireNonNull(endereco).strip();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getTelefone() { return telefone; }
    public String getEndereco() { return endereco; }
}
