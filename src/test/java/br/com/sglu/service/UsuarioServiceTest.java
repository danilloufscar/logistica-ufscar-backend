package br.com.sglu.service;

import br.com.sglu.exception.EmailJaCadastradoException;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock UsuarioRepository repository;
    @Mock PasswordEncoder encoder;
    UsuarioService service;

    @BeforeEach
    void setUp() { service = new UsuarioService(repository, encoder); }

    @Test
    void normalizaDadosEPersisteSomenteSenhaCodificada() {
        when(encoder.encode(" senha-teste ")).thenReturn("hash-bcrypt");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.cadastrar(" Ana ", " ANA@example.com ", " senha-teste ", Perfil.OPERADOR);

        verify(repository).existsByEmail("ana@example.com");
        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(result).isSameAs(captor.getValue());
        assertThat(result.getNome()).isEqualTo("Ana");
        assertThat(result.getEmail()).isEqualTo("ana@example.com");
        assertThat(result.getPerfil()).isEqualTo(Perfil.OPERADOR);
        assertThat(result.getSenhaHash()).isEqualTo("hash-bcrypt");
        verify(encoder).encode(" senha-teste ");
    }

    @Test
    void rejeitaEmailDuplicadoAntesDeCodificarOuSalvar() {
        when(repository.existsByEmail("ana@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.cadastrar("Ana", "ANA@example.com", "senha", Perfil.ADMINISTRADOR))
                .isInstanceOf(EmailJaCadastradoException.class);
        verifyNoInteractions(encoder);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void listaComOrdenacaoDeterministicaPorNomeEId() {
        var usuario = new Usuario("Ana", "ana@example.com", "hash", Perfil.OPERADOR);
        when(repository.findAll(Sort.by("nome", "id"))).thenReturn(List.of(usuario));
        assertThat(service.listar()).containsExactly(usuario);
    }
}
