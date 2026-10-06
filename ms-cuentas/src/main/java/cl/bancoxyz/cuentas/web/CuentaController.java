package cl.bancoxyz.cuentas.web;

import cl.bancoxyz.cuentas.dominio.Cuenta;
import cl.bancoxyz.cuentas.servicio.CuentaService;
import cl.bancoxyz.cuentas.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    private final CuentaService servicio;

    public CuentaController(CuentaService servicio) {
        this.servicio = servicio;
    }

    /** Apertura de cuenta. */
    @PostMapping
    public ResponseEntity<CuentaResponse> abrir(@Valid @RequestBody AbrirCuentaRequest req) {
        Cuenta cuenta = servicio.abrir(req);
        return ResponseEntity.created(URI.create("/api/cuentas/" + cuenta.getId())).body(CuentaResponse.de(cuenta));
    }

    @GetMapping("/{id}")
    public CuentaResponse obtener(@PathVariable Long id) {
        return CuentaResponse.de(servicio.obtener(id));
    }

    @GetMapping
    public List<CuentaResponse> porCliente(@RequestParam Long clienteId) {
        return servicio.listarPorCliente(clienteId).stream().map(CuentaResponse::de).toList();
    }

    @GetMapping("/{id}/saldo")
    public SaldoResponse saldo(@PathVariable Long id) {
        Cuenta c = servicio.obtener(id);
        return new SaldoResponse(c.getId(), c.getNumero(), c.getSaldo(), c.getEstado());
    }

    @GetMapping("/{id}/movimientos")
    public List<MovimientoResponse> movimientos(@PathVariable Long id, @RequestParam(defaultValue = "20") int limite) {
        Long clienteId = servicio.obtener(id).getClienteId();
        return servicio.ultimosMovimientos(id, limite).stream()
                .map(m -> MovimientoResponse.de(m, clienteId, false)).toList();
    }

    /** Débito o crédito idempotente (lo usa ms-pagos). */
    @PostMapping("/{id}/movimientos")
    public ResponseEntity<MovimientoResponse> registrarMovimiento(@PathVariable Long id,
                                                                  @Valid @RequestBody MovimientoRequest req) {
        MovimientoResponse resp = servicio.registrarMovimiento(id, req);
        return ResponseEntity.status(resp.duplicado() ? HttpStatus.OK : HttpStatus.CREATED).body(resp);
    }

    /** Mantenimiento: bloquear / activar. */
    @PatchMapping("/{id}/estado")
    public CuentaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest req) {
        return CuentaResponse.de(servicio.cambiarEstado(id, req.estado()));
    }

    /** Cierre de cuenta (sólo con saldo 0). */
    @DeleteMapping("/{id}")
    public CuentaResponse cerrar(@PathVariable Long id) {
        return CuentaResponse.de(servicio.cerrar(id));
    }

    /** Validación de PIN para cajeros automáticos. Bloquea la cuenta tras N intentos fallidos. */
    @PostMapping("/{id}/validar-pin")
    public ValidarPinResponse validarPin(@PathVariable Long id, @Valid @RequestBody ValidarPinRequest req) {
        return servicio.validarPin(id, req.pin());
    }
}
