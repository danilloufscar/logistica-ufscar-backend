package br.com.sglu.service;

import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;
import br.com.sglu.security.UsuarioPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutenticacaoServiceTest {
    @Mock AuthenticationManager manager;
    @Mock TokenService tokens;
    AutenticacaoService service;

    @BeforeEach
    void setUp() { service = new AutenticacaoService(manager, tokens); }

    @Test
    void emiteTokenSomenteDepoisDeAutenticar() {
        var principal = new UsuarioPrincipal(new Usuario("Ana", "ana@example.com", "hash", Perfil.OPERADOR));
        when(manager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(tokens.gerar(principal)).thenReturn("token-assinado");

        assertThat(service.autenticar("ana@example.com", "senha")).isEqualTo("token-assinado");
        verify(manager).authenticate(UsernamePasswordAuthenticationToken.unauthenticated("ana@example.com", "senha"));
        verify(tokens).gerar(principal);
    }

    @Test
    void naoEmiteTokenParaCredenciaisInvalidas() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("inválidas"));
        assertThatThrownBy(() -> service.autenticar("ana@example.com", "incorreta"))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tokens);
    }

    @Test
    void rejeitaSenhaQueUltrapassaLimiteEmBytesDoBCrypt() {
        assertThatThrownBy(() -> service.autenticar("ana@example.com", "á".repeat(37)))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(manager, tokens);
    }
}
