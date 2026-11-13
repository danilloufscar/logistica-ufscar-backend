package br.com.sglu.dto.pedido;

import br.com.sglu.model.HistoricoStatusPedido;
import br.com.sglu.model.OcorrenciaPedido;
import br.com.sglu.model.StatusPedido;
import br.com.sglu.model.TipoOcorrencia;
import java.time.Instant;
import java.time.LocalDate;

public record TimelineEventoResponse(
        TipoEvento tipo,
        Instant dataHora,
        LocalDate dataOcorrencia,
        UsuarioResumo usuario,
        StatusPedido status,
        String statusRotulo,
        TipoOcorrencia tipoOcorrencia,
        String tipoOcorrenciaRotulo,
        String descricao) {
    public enum TipoEvento {
        STATUS,
        OCORRENCIA
    }

    public record UsuarioResumo(Long id, String nome) {
    }

    public static TimelineEventoResponse deStatus(HistoricoStatusPedido h) {
        return new TimelineEventoResponse(
                TipoEvento.STATUS,
                h.getDataHora(),
                null,
                new UsuarioResumo(h.getUsuario().getId(), h.getUsuario().getNome()),
                h.getStatus(),
                h.getStatus().getRotulo(),
                null,
                null,
                "Status alterado para " + h.getStatus().getRotulo());
    }

    public static TimelineEventoResponse deOcorrencia(OcorrenciaPedido o) {
        return new TimelineEventoResponse(
                TipoEvento.OCORRENCIA,
                o.getDataHora(),
                o.getDataOcorrencia(),
                new UsuarioResumo(o.getUsuario().getId(), o.getUsuario().getNome()),
                null,
                null,
                o.getTipo(),
                o.getTipo().getRotulo(),
                o.getDescricao());
    }
}