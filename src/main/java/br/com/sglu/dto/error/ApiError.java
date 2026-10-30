package br.com.sglu.dto.error;

import java.util.Map;

public record ApiError(String codigo, String mensagem, Map<String, String> detalhes) {
    public ApiError(String codigo, String mensagem) {
        this(codigo, mensagem, Map.of());
    }
}
