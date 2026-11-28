package br.com.sglu.service;

import br.com.sglu.exception.DadosInvalidosException;
import br.com.sglu.repository.RelatorioRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RelatorioServiceTest {
    private final RelatorioRepository repository = mock(RelatorioRepository.class);
    private final RelatorioService service = new RelatorioService(repository);
    private final LocalDate dia = LocalDate.of(2026, 11, 26);

    private RelatorioRepository.Entrega entrega(String momento) {
        var entrega = mock(RelatorioRepository.Entrega.class);
        when(entrega.getPrazo()).thenReturn(dia);
        when(entrega.getCriadoEm()).thenReturn(Instant.parse("2026-11-26T03:00:00Z"));
        when(entrega.getEntregueEm()).thenReturn(Instant.parse(momento));
        return entrega;
    }

    @Test
    void calculaCemPorCentoAteUltimoSegundoDoPrazoLocal() {
        var pontual = entrega("2026-11-27T02:59:59Z");
        when(repository.buscarEntregas(any(), any())).thenReturn(List.of(pontual));
        assertThat(service.consultar(dia, dia).percentualNoPrazo()).isEqualByComparingTo("100.00");
        verify(repository).buscarEntregas(Instant.parse("2026-11-26T03:00:00Z"), Instant.parse("2026-11-27T03:00:00Z"));
    }

    @Test
    void zeroPorCentoEUmResultadoDiferenteDeAusenciaDeEntregas() {
        var atrasada = entrega("2026-11-27T03:00:00Z");
        when(repository.buscarEntregas(any(), any())).thenReturn(List.of(atrasada));
        assertThat(service.consultar(dia, dia.plusDays(1)).percentualNoPrazo()).isEqualByComparingTo("0.00");
        when(repository.buscarEntregas(any(), any())).thenReturn(List.of());
        assertThat(service.consultar(dia, dia).percentualNoPrazo()).isNull();
        assertThat(service.consultar(dia, dia).tempoMedioEntregaDias()).isNull();
    }

    @Test
    void mediaUsaDuracaoFracionariaEArredondaSomenteOResultado() {
        var primeira = entrega("2026-11-27T15:00:00Z"); // 1,5 dia
        var segunda = entrega("2026-11-26T15:00:00Z"); // 0,5 dia
        when(repository.buscarEntregas(any(), any())).thenReturn(List.of(primeira, segunda));
        assertThat(service.consultar(dia, dia.plusDays(1)).tempoMedioEntregaDias()).isEqualByComparingTo("1.00");
    }

    @Test
    void mediaDistingueEntregaImediataDePeriodoSemEntregas() {
        var imediata = entrega("2026-11-26T03:00:00Z");
        when(repository.buscarEntregas(any(), any())).thenReturn(List.of(imediata));
        assertThat(service.consultar(dia, dia).tempoMedioEntregaDias()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejeitaDatasAusentesInvertidasOuForaDoFormatoDaApiSemConsultarBanco() {
        assertThatThrownBy(() -> service.consultar(null, dia)).isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> service.consultar(dia, null)).isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> service.consultar(dia.plusDays(1), dia)).isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> service.consultar(LocalDate.of(0, 1, 1), dia)).isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> service.consultar(dia, LocalDate.MAX)).isInstanceOf(DadosInvalidosException.class);
        verifyNoInteractions(repository);
    }
}
