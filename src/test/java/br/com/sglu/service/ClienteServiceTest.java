package br.com.sglu.service;

import br.com.sglu.model.Cliente;
import br.com.sglu.repository.ClienteRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {
    @Mock ClienteRepository repository;
    @InjectMocks ClienteService service;

    @Test
    void cadastraPreservandoFormatacaoInternaETirandoEspacosExternos() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var cliente = service.cadastrar(" Cliente Teste ", " +55 (16) 99999-0000 ", " Rua Fictícia, 10 ");
        assertThat(cliente.getNome()).isEqualTo("Cliente Teste");
        assertThat(cliente.getTelefone()).isEqualTo("+55 (16) 99999-0000");
        assertThat(cliente.getEndereco()).isEqualTo("Rua Fictícia, 10");
        verify(repository).saveAndFlush(cliente);
    }

    @Test
    void listaPorNomeEId() {
        var cliente = new Cliente("Cliente", "123", "Endereço fictício");
        when(repository.findAll(Sort.by("nome", "id"))).thenReturn(List.of(cliente));
        assertThat(service.listar()).containsExactly(cliente);
    }
}
