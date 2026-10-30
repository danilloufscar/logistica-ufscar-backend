package br.com.sglu.repository;

import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPerfil(Perfil perfil);
}
