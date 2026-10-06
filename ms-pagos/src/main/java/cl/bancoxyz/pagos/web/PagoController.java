package cl.bancoxyz.pagos.web;

import cl.bancoxyz.pagos.dominio.Pago;
import cl.bancoxyz.pagos.dominio.TipoPago;
import cl.bancoxyz.pagos.servicio.PagoService;
import cl.bancoxyz.pagos.web.Dtos.OperacionRequest;
import cl.bancoxyz.pagos.web.Dtos.PagoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Códigos de respuesta:
 *  201 COMPLETADO | 202 PENDIENTE (se reintentará) | 422 RECHAZADO/REVERTIDO | 200 operación repetida
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService servicio;

    public PagoController(PagoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/transferencias")
    public ResponseEntity<PagoResponse> transferir(@Valid @RequestBody OperacionRequest req,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return responder(servicio.registrar(TipoPago.TRANSFERENCIA, req, key));
    }

    @PostMapping("/servicios")
    public ResponseEntity<PagoResponse> pagarServicio(@Valid @RequestBody OperacionRequest req,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return responder(servicio.registrar(TipoPago.PAGO_SERVICIO, req, key));
    }

    @PostMapping("/depositos")
    public ResponseEntity<PagoResponse> depositar(@Valid @RequestBody OperacionRequest req,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return responder(servicio.registrar(TipoPago.DEPOSITO, req, key));
    }

    @PostMapping("/retiros")
    public ResponseEntity<PagoResponse> retirar(@Valid @RequestBody OperacionRequest req,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return responder(servicio.registrar(TipoPago.RETIRO, req, key));
    }

    @GetMapping("/{referencia}")
    public PagoResponse obtener(@PathVariable String referencia) {
        return PagoResponse.de(servicio.obtener(referencia));
    }

    @GetMapping
    public List<PagoResponse> deCuenta(@RequestParam Long cuentaId, @RequestParam(defaultValue = "20") int limite) {
        return servicio.deCuenta(cuentaId, limite).stream().map(PagoResponse::de).toList();
    }

    private static ResponseEntity<PagoResponse> responder(PagoService.Resultado r) {
        Pago p = r.pago();
        HttpStatus status = r.repetido() ? HttpStatus.OK : switch (p.getEstado()) {
            case COMPLETADO -> HttpStatus.CREATED;
            case PENDIENTE -> HttpStatus.ACCEPTED;
            case RECHAZADO, REVERTIDO -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(PagoResponse.de(p));
    }
}
