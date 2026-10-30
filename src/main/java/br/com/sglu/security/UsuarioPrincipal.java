package br.com.sglu.security;

import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class UsuarioPrincipal extends User {

    private final Long id;
    private final String nome;
    private final Perfil perfil;

    public UsuarioPrincipal(Usuario usuario) {
        super(usuario.getEmail(), usuario.getSenhaHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())));
        this.id = usuario.getId();
        this.nome = usuario.getNome();
        this.perfil = usuario.getPerfil();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public Perfil getPerfil() { return perfil; }
}
