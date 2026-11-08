package br.com.sglu.service;

import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdministradorInicialServiceTest {
    @Mock UsuarioRepository repository;
    @Mock PasswordEncoder encoder;
    AdministradorInicialService service;

    @BeforeEach
    void setUp() { service = new AdministradorInicialService(repository, encoder); }

    @Test
    void criaAdministradorComEmailNormalizadoESenhaCodificada() {
        when(encoder.encode("senha-segura-123")).thenReturn("hash-bcrypt");
        service.inicializar(" Ana ", " ANA@example.com ", "senha-segura-123");
        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNome()).isEqualTo("Ana");
        assertThat(captor.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(captor.getValue().getSenhaHash()).isEqualTo("hash-bcrypt");
        assertThat(captor.getValue().getPerfil()).isEqualTo(Perfil.ADMINISTRADOR);
    }

    @Test
    void naoAlteraAdministradorExistente() {
        when(repository.existsByPerfil(Perfil.ADMINISTRADOR)).thenReturn(true);
        service.inicializar("Ana", "ana@example.com", "outra-senha");
        verify(repository, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void naoPromoveOperadorComMesmoEmail() {
        when(repository.existsByEmail("ana@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.inicializar("Ana", "ANA@example.com", "senha"))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(encoder);
    }
}
