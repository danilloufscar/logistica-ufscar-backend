package br.com.sglu.service;

import br.com.sglu.exception.EmailJaCadastradoException;
import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;
import br.com.sglu.repository.UsuarioRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario cadastrar(String nome, String email, String senha, Perfil perfil) {
        String emailNormalizado = email.strip().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(emailNormalizado)) {
            throw new EmailJaCadastradoException();
        }
        var usuario = new Usuario(nome, emailNormalizado, passwordEncoder.encode(senha), perfil);
        // A restrição UNIQUE do banco também protege contra cadastros concorrentes.
        return repository.saveAndFlush(usuario);
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return repository.findAll(Sort.by("nome", "id"));
    }
}
