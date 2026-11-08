package br.com.sglu.service;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeradorCodigoConsultaTest {
    @Test
    void geraOitoCaracteresAlfanumericosAleatoriosSemCaracteresAmbiguos() {
        var gerador = new GeradorCodigoConsulta();
        var codigos = new HashSet<String>();
        for (int i = 0; i < 1000; i++) codigos.add(gerador.gerar());

        assertThat(codigos).hasSize(1000).allMatch(codigo -> codigo.matches("[A-HJ-NP-Z2-9]{8}"));
    }
}
