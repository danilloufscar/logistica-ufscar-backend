package br.com.sglu.dto.estoque;

import br.com.sglu.model.MovimentacaoEstoque;
import br.com.sglu.model.TipoMovimentacaoEstoque;
import java.time.Instant;
import java.time.LocalDate;

public record AjusteEstoqueResponse(
        Long id,
        Long produtoId,
        String produtoNome,
        TipoMovimentacaoEstoque tipo,
        Integer quantidadeAjustada,
        Integer saldoAnterior,
        Integer saldoAtual,
        String justificativa,
        LocalDate data,
        Instant registradoEm,
        String usuarioResponsavel) {
    public static AjusteEstoqueResponse from(MovimentacaoEstoque m, int saldoAnterior, int saldoAtual) {
        return new AjusteEstoqueResponse(
                m.getId(),
                m.getProduto().getId(),
                m.getProduto().getNome(),
                m.getTipo(),
                m.getQuantidade(),
                saldoAnterior,
                saldoAtual,
                m.getJustificativa(),
                m.getData(),
                m.getRegistradoEm(),
                m.getUsuario().getNome());
    }
}