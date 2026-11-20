package br.com.sglu.controller;

import br.com.sglu.dto.pedido.AcompanhamentoPedidoResponse;
import br.com.sglu.service.PedidoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/acompanhamento")
public class AcompanhamentoController {

    private final PedidoService service;

    public AcompanhamentoController(PedidoService service) {
        this.service = service;
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<AcompanhamentoPedidoResponse> acompanhar(@PathVariable String codigo) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.acompanharPorCodigo(codigo));
    }
}