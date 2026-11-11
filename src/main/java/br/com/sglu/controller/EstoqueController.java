package br.com.sglu.controller;

import br.com.sglu.dto.estoque.EntradaEstoqueRequest;
import br.com.sglu.dto.estoque.EntradaEstoqueResponse;
import br.com.sglu.exception.SessaoInvalidaException;
import br.com.sglu.service.EstoqueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/estoque/entradas")
public class EstoqueController {
    private final EstoqueService service;

    public EstoqueController(EstoqueService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntradaEstoqueResponse registrar(@Valid @RequestBody EntradaEstoqueRequest request, @AuthenticationPrincipal Jwt jwt) {
        Object id = jwt == null ? null : jwt.getClaim("id");
        if (!(id instanceof Number numero)) throw new SessaoInvalidaException();
        return service.registrarEntrada(request, numero.longValue());
    }
}
