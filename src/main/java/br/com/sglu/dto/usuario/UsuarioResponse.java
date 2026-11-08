package br.com.sglu.dto.usuario;

import br.com.sglu.model.Perfil;
import br.com.sglu.model.Usuario;

public record UsuarioResponse(Long id, String nome, String email, Perfil perfil) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil());
    }
}
