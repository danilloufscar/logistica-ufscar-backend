package br.com.sglu.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class GeradorCodigoConsulta {
    static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int TAMANHO = 8;

    private final SecureRandom random = new SecureRandom();

    public String gerar() {
        var codigo = new StringBuilder(TAMANHO);
        for (int i = 0; i < TAMANHO; i++) {
            codigo.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }
}
