package br.com.sglu.dto.relatorio;

import br.com.sglu.model.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioDesempenhoResponse(
        LocalDate inicio, LocalDate fim, List<QuantidadePorStatus> pedidosPorStatus,
        long totalPedidos, long entregasNoPeriodo, long entregasNoPrazo,
        BigDecimal percentualNoPrazo, BigDecimal tempoMedioEntregaDias) {
    public record QuantidadePorStatus(StatusPedido status, String rotulo, long quantidade) {}
}
