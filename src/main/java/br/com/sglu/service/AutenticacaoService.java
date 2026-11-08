package br.com.sglu.service;

import br.com.sglu.security.UsuarioPrincipal;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AutenticacaoService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AutenticacaoService(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    public String autenticar(String email, String senha) {
        // BCrypt limits passwords by UTF-8 bytes, not by Java character count.
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Credenciais inválidas");
        }
        var credentials = UsernamePasswordAuthenticationToken.unauthenticated(email, senha);
        var authentication = authenticationManager.authenticate(credentials);
        return tokenService.gerar((UsuarioPrincipal) authentication.getPrincipal());
    }
}
