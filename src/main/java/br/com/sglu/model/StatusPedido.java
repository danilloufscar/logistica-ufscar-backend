package br.com.sglu.model;

import java.util.Optional;

public enum StatusPedido {
    CRIADO("Criado"),
    EM_SEPARACAO("Em separação"),
    DESPACHADO("Despachado"),
    EM_TRANSITO("Em trânsito"),
    ENTREGUE("Entregue"),
    CANCELADO("Cancelado");

    private final String rotulo;

    StatusPedido(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() { return rotulo; }

    public Optional<StatusPedido> proximo() {
        return switch (this) {
            case CRIADO -> Optional.of(EM_SEPARACAO);
            case EM_SEPARACAO -> Optional.of(DESPACHADO);
            case DESPACHADO -> Optional.of(EM_TRANSITO);
            case EM_TRANSITO -> Optional.of(ENTREGUE);
            case ENTREGUE, CANCELADO -> Optional.empty();
        };
    }

    public boolean permiteAlteracao() {
        return this == CRIADO || this == EM_SEPARACAO;
    }

    public boolean isEncerrado() {
        return this == ENTREGUE || this == CANCELADO;
    }
}
