package br.com.sglu.exception;

public class OperacaoNaoPermitidaException extends RuntimeException {
    private final String codigo;

    public OperacaoNaoPermitidaException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() { return codigo; }
}
