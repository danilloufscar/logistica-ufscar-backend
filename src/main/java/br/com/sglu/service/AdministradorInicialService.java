package br.com.sglu.service;

import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.UsuarioRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministradorInicialService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AdministradorInicialService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void inicializar(String nome, String email, String senha) {
        if (repository.existsByPerfil(Perfil.ADMINISTRADOR)) {
            return;
        }
        String emailNormalizado = email.strip().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(emailNormalizado)) {
            throw new IllegalStateException("Não foi possível criar o Administrador inicial: e-mail já cadastrado");
        }
        repository.save(new Usuario(nome, emailNormalizado, passwordEncoder.encode(senha), Perfil.ADMINISTRADOR));
    }
}
