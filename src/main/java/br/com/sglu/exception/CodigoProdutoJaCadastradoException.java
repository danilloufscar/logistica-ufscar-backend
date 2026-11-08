package br.com.sglu.exception;

public class CodigoProdutoJaCadastradoException extends RuntimeException {
    public CodigoProdutoJaCadastradoException() {
        super("Código de produto já cadastrado");
    }
}
