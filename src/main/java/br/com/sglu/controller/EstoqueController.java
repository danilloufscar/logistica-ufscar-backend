package br.com.sglu.controller;

import br.com.sglu.dto.estoque.AjusteEstoqueRequest;
import br.com.sglu.dto.estoque.AjusteEstoqueResponse;
import br.com.sglu.dto.estoque.EntradaEstoqueRequest;
import br.com.sglu.dto.estoque.EntradaEstoqueResponse;
import br.com.sglu.dto.estoque.MovimentacaoEstoqueResponse;
import br.com.sglu.dto.estoque.SaldoEstoqueResponse;
import br.com.sglu.exception.SessaoInvalidaException;
import br.com.sglu.service.EstoqueService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/estoque")
public class EstoqueController {
    private final EstoqueService service;

    public EstoqueController(EstoqueService service) {
        this.service = service;
    }

    @GetMapping
    public List<SaldoEstoqueResponse> listar() {
        return service.listarSaldos();
    }

    @PostMapping("/entradas")
    @ResponseStatus(HttpStatus.CREATED)
    public EntradaEstoqueResponse registrar(@Valid @RequestBody EntradaEstoqueRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Object id = jwt == null ? null : jwt.getClaim("id");
        if (!(id instanceof Number numero))
            throw new SessaoInvalidaException();
        return service.registrarEntrada(request, numero.longValue());
    }

    @PostMapping("/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    public AjusteEstoqueResponse ajustarInventario(@Valid @RequestBody AjusteEstoqueRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Object id = jwt == null ? null : jwt.getClaim("id");
        if (!(id instanceof Number numero))
            throw new SessaoInvalidaException();
        return service.ajustarInventario(request, numero.longValue());
    }

    @GetMapping("/{produtoId}/movimentacoes")
    public List<MovimentacaoEstoqueResponse> listarMovimentacoes(@PathVariable Long produtoId) {
        return service.listarMovimentacoesPorProduto(produtoId);
    }
}
