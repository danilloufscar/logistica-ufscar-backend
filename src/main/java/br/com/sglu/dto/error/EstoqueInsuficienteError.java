package br.com.sglu.dto.error;

import br.com.sglu.exception.EstoqueInsuficienteException;
import java.util.List;
import java.util.Map;

public record EstoqueInsuficienteError(String codigo, String mensagem, Map<String, String> detalhes,
                                      List<EstoqueInsuficienteException.Item> itens) {
    public static EstoqueInsuficienteError from(EstoqueInsuficienteException erro) {
        return new EstoqueInsuficienteError("ESTOQUE_INSUFICIENTE", erro.getMessage(), Map.of(), erro.getItens());
    }
}
