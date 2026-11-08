package br.com.sglu.service;

import br.com.sglu.security.UsuarioPrincipal;

public interface TokenService {
    String gerar(UsuarioPrincipal usuario);
}
