package br.com.sglu.service;

import br.com.sglu.dto.relatorio.RelatorioDesempenhoResponse;
import br.com.sglu.dto.relatorio.RelatorioDesempenhoResponse.QuantidadePorStatus;
import br.com.sglu.exception.DadosInvalidosException;
import br.com.sglu.model.StatusPedido;
import br.com.sglu.repository.RelatorioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelatorioService {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private final RelatorioRepository repository;

    public RelatorioService(RelatorioRepository repository) {
        this.repository = repository;
    }

    /** Datas inclusivas; as consultas usam início inclusivo e início do dia seguinte exclusivo. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RelatorioDesempenhoResponse consultar(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        var de = inicio.atStartOfDay(FUSO).toInstant();
        var ate = fim.plusDays(1).atStartOfDay(FUSO).toInstant();
        var contagens = new EnumMap<StatusPedido, Long>(StatusPedido.class);
        repository.contarPorStatus(de, ate).forEach(c -> contagens.put(c.getStatus(), c.getQuantidade()));
        var porStatus = Arrays.stream(StatusPedido.values())
                .filter(status -> status != StatusPedido.CANCELADO)
                .map(status -> new QuantidadePorStatus(status, status.getRotulo(), contagens.getOrDefault(status, 0L)))
                .toList();
        var entregas = repository.buscarEntregas(de, ate);
        long noPrazo = entregas.stream()
                .filter(e -> !e.getEntregueEm().atZone(FUSO).toLocalDate().isAfter(e.getPrazo()))
                .count();
        BigDecimal percentual = entregas.isEmpty() ? null : BigDecimal.valueOf(noPrazo)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(entregas.size()), 2, RoundingMode.HALF_UP);
        return new RelatorioDesempenhoResponse(inicio, fim, porStatus,
                porStatus.stream().mapToLong(QuantidadePorStatus::quantidade).sum(),
                entregas.size(), noPrazo, percentual, tempoMedio(entregas));
    }

    /** RF19: duração decorrida em dias de 24 horas; arredonda apenas o resultado final. */
    private BigDecimal tempoMedio(List<RelatorioRepository.Entrega> entregas) {
        if (entregas.isEmpty()) return null;
        BigDecimal totalMilissegundos = entregas.stream()
                .map(e -> BigDecimal.valueOf(Duration.between(e.getCriadoEm(), e.getEntregueEm()).toMillis()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalMilissegundos.divide(BigDecimal.valueOf(86_400_000L)
                .multiply(BigDecimal.valueOf(entregas.size())), 2, RoundingMode.HALF_UP);
    }

    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null)
            throw new DadosInvalidosException(Map.of("periodo", "Informe a data inicial e a data final"));
        if (inicio.getYear() < 1 || fim.getYear() > 9999 || inicio.isAfter(fim))
            throw new DadosInvalidosException(Map.of("periodo", "Informe um período válido, com início até a data final"));
    }
}
