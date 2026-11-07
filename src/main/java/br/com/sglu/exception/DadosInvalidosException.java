package br.com.sglu.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DadosInvalidosException extends RuntimeException {
    private final Map<String, String> detalhes;

    public DadosInvalidosException(Map<String, String> detalhes) {
        super("Verifique os campos informados");
        this.detalhes = Collections.unmodifiableMap(new LinkedHashMap<>(detalhes));
    }

    public Map<String, String> getDetalhes() { return detalhes; }
}