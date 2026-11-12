package br.com.sglu.dto.estoque;

import br.com.sglu.model.MovimentacaoEstoque;
import br.com.sglu.model.TipoMovimentacaoEstoque;
import java.time.Instant;
import java.time.LocalDate;

public record MovimentacaoEstoqueResponse(
        Long id,
        Long produtoId,
        String produtoCodigo,
        String produtoNome,
        TipoMovimentacaoEstoque tipo,
        Integer quantidade,
        LocalDate data,
        Instant registradoEm,
        String usuarioResponsavel,
        Long pedidoId,
        String justificativa) {
    public static MovimentacaoEstoqueResponse from(MovimentacaoEstoque m) {
        return new MovimentacaoEstoqueResponse(
                m.getId(),
                m.getProduto().getId(),
                m.getProduto().getCodigo(),
                m.getProduto().getNome(),
                m.getTipo(),
                m.getQuantidade(),
                m.getData(),
                m.getRegistradoEm(),
                m.getUsuario().getNome(),
                m.getPedido() != null ? m.getPedido().getId() : null,
                m.getJustificativa());
    }
}