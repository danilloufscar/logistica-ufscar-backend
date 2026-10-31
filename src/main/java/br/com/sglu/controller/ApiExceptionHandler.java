package br.com.sglu.controller;

import br.com.sglu.dto.error.ApiError;
import br.com.sglu.exception.EmailJaCadastradoException;
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
