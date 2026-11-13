package br.com.sglu.dto.pedido;

import br.com.sglu.model.TipoOcorrencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record OcorrenciaPedidoRequest(
        @NotNull(message = "O tipo da ocorrência é obrigatório") TipoOcorrencia tipo,

        @NotBlank(message = "A descrição da ocorrência é obrigatória") @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres") String descricao,

        @NotNull(message = "A data da ocorrência é obrigatória") LocalDate data) {
    // Mantém compatibilidade com PedidoTimelineIntegrationTest
    public OcorrenciaPedidoRequest(String descricao) {
        this(TipoOcorrencia.ATRASO, descricao, LocalDate.now());
    }
}