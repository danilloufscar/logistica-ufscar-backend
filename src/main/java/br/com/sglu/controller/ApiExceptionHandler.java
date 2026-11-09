package br.com.sglu.controller;

import br.com.sglu.dto.error.ApiError;
import br.com.sglu.exception.EmailJaCadastradoException;
import br.com.sglu.exception.CodigoProdutoJaCadastradoException;
import br.com.sglu.exception.DadosInvalidosException;
import br.com.sglu.exception.OperacaoNaoPermitidaException;
import br.com.sglu.exception.PedidoNaoEncontradoException;
import br.com.sglu.exception.SessaoInvalidaException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DadosInvalidosException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidData(DadosInvalidosException exception) {
        return new ApiError("DADOS_INVALIDOS", exception.getMessage(), exception.getDetalhes());
    }

    @ExceptionHandler(PedidoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError orderNotFound(PedidoNaoEncontradoException exception) {
        return new ApiError("PEDIDO_NAO_ENCONTRADO", exception.getMessage());
    }

    @ExceptionHandler(OperacaoNaoPermitidaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError operationNotAllowed(OperacaoNaoPermitidaException exception) {
        return new ApiError(exception.getCodigo(), exception.getMessage());
    }

    @ExceptionHandler(SessaoInvalidaException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError invalidSession(SessaoInvalidaException exception) {
        return new ApiError("NAO_AUTENTICADO", exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError concurrentUpdate(OptimisticLockingFailureException exception) {
        return new ApiError("PEDIDO_DESATUALIZADO", "O pedido foi alterado por outra operação. Atualize a página e tente novamente");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidParameter(MethodArgumentTypeMismatchException exception) {
        return new ApiError("PARAMETRO_INVALIDO", "Verifique os parâmetros informados",
                Map.of(exception.getName(), "Valor inválido"));
    }

    @ExceptionHandler(CodigoProdutoJaCadastradoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError productCodeAlreadyRegistered(CodigoProdutoJaCadastradoException exception) {
        return new ApiError("CODIGO_PRODUTO_JA_CADASTRADO", exception.getMessage());
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError emailAlreadyRegistered(EmailJaCadastradoException exception) {
        return new ApiError("EMAIL_JA_CADASTRADO", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError dataConflict(DataIntegrityViolationException exception) {
        return new ApiError("CONFLITO_DADOS", "Não foi possível salvar os dados devido a uma restrição de integridade");
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError authenticationFailed(AuthenticationException exception) {
        return new ApiError("CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validationFailed(MethodArgumentNotValidException exception) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                detalhes.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return new ApiError("DADOS_INVALIDOS", "Verifique os campos informados", detalhes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError unreadableBody(HttpMessageNotReadableException exception) {
        return new ApiError("JSON_INVALIDO", "Corpo da requisição inválido");
    }
}
