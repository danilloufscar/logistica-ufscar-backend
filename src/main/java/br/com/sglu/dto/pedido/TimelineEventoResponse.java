package br.com.sglu.dto.pedido;

import br.com.sglu.model.HistoricoStatusPedido;
import br.com.sglu.model.OcorrenciaPedido;
import br.com.sglu.model.StatusPedido;
import java.time.Instant;

public record TimelineEventoResponse(
        TipoEvento tipo,
        Instant dataHora,
        UsuarioResumo usuario,
        StatusPedido status,
        String statusRotulo,
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
                new UsuarioResumo(h.getUsuario().getId(), h.getUsuario().getNome()),
                h.getStatus(),
                h.getStatus().getRotulo(),
                "Status alterado para " + h.getStatus().getRotulo());
    }

    public static TimelineEventoResponse deOcorrencia(OcorrenciaPedido o) {
        return new TimelineEventoResponse(
                TipoEvento.OCORRENCIA,
                o.getDataHora(),
                new UsuarioResumo(o.getUsuario().getId(), o.getUsuario().getNome()),
                null,
                null,
                o.getDescricao());
    }
}