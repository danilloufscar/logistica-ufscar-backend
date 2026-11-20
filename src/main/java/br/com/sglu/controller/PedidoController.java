package br.com.sglu.controller;

import br.com.sglu.dto.pedido.AvancoStatusRequest;
import br.com.sglu.dto.pedido.OcorrenciaPedidoRequest;
import br.com.sglu.dto.pedido.PedidoRequest;
import br.com.sglu.dto.pedido.PedidoResponse;
import br.com.sglu.dto.pedido.PedidoResumoResponse;
import br.com.sglu.dto.pedido.TimelineEventoResponse;
import br.com.sglu.exception.SessaoInvalidaException;
import br.com.sglu.model.StatusPedido;
import br.com.sglu.service.PedidoService;
import br.com.sglu.service.PedidoCadastroService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    private final PedidoService service;
    private final PedidoCadastroService cadastro;

    public PedidoController(PedidoService service, PedidoCadastroService cadastro) {
        this.service = service;
        this.cadastro = cadastro;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse criar(@Valid @RequestBody PedidoRequest request, @AuthenticationPrincipal Jwt jwt) {
        return cadastro.criar(request, usuarioId(jwt));
    }

    @GetMapping
    public List<PedidoResumoResponse> listar(@RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Long clienteId) {
        return service.listar(status, clienteId);
    }

    @GetMapping("/{id}")
    public PedidoResponse detalhar(@PathVariable Long id) {
        return service.detalhar(id);
    }

    @PutMapping("/{id}")
    public PedidoResponse editar(@PathVariable Long id, @Valid @RequestBody PedidoRequest request) {
        return service.editar(id, request);
    }

    @PostMapping("/{id}/cancelar")
    public PedidoResponse cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.cancelar(id, usuarioId(jwt));
    }

    @PostMapping("/{id}/avancar")
    public PedidoResponse avancar(@PathVariable Long id, @Valid @RequestBody AvancoStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.avancar(id, request.statusAtual(), usuarioId(jwt));
    }

    // O claim "id" é gravado no token pelo JwtTokenService.
    private static Long usuarioId(Jwt jwt) {
        Object id = jwt == null ? null : jwt.getClaim("id");
        if (id instanceof Number numero)
            return numero.longValue();
        throw new SessaoInvalidaException();
    }

    @PostMapping("/{id}/ocorrencias")
    @ResponseStatus(HttpStatus.CREATED)
    public TimelineEventoResponse registrarOcorrencia(@PathVariable Long id,
            @Valid @RequestBody OcorrenciaPedidoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.registrarOcorrencia(id, request, usuarioId(jwt));
    }

    @GetMapping("/{id}/timeline")
    public org.springframework.http.ResponseEntity<List<TimelineEventoResponse>> timeline(@PathVariable Long id) {
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(service.timeline(id));
    }

}
