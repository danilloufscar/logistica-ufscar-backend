package br.com.sglu.dto.cliente;

import br.com.sglu.model.Cliente;

public record ClienteResponse(Long id, String nome, String telefone, String endereco) {
    public static ClienteResponse from(Cliente cliente) {
        return new ClienteResponse(cliente.getId(), cliente.getNome(), cliente.getTelefone(), cliente.getEndereco());
    }
}
