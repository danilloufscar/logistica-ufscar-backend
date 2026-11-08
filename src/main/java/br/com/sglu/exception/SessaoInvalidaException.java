package br.com.sglu.exception;

public class SessaoInvalidaException extends RuntimeException {
    public SessaoInvalidaException() {
        super("Sessão inválida. Entre novamente");
    }
}
