package cl.bancoxyz.bff.movil.web;

import cl.bancoxyz.bff.movil.integracion.BancoApiClient;
import cl.bancoxyz.bff.movil.integracion.Modelos.*;
import cl.bancoxyz.bff.movil.integracion.ServicioNoDisponibleException;
import cl.bancoxyz.bff.movil.web.DtosMovil.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** API del canal MÓVIL: pocas llamadas, respuestas pequeñas y rápidas. */
@RestController
@RequestMapping("/movil")
public class MovilController {

    private final BancoApiClient api;
    private final ExecutorService paralelo = Executors.newVirtualThreadPerTaskExecutor();

    public MovilController(BancoApiClient api) {
        this.api = api;
    }

    /** Pantalla de inicio de la app en UNA sola llamada (nombre, cuentas y 5 últimos movimientos). */
    @GetMapping("/clientes/{clienteId}/inicio")
    public Inicio inicio(@PathVariable Long clienteId) {
        CompletableFuture<ClienteDto> cliente = CompletableFuture.supplyAsync(() -> api.cliente(clienteId), paralelo);
        List<CuentaDto> cuentas = api.cuentasDeCliente(clienteId);

        boolean parcial = false;
        List<OperacionResumida> ultimas = new ArrayList<>();
        if (!cuentas.isEmpty()) {
            try {
                // Sólo la cuenta principal: el detalle de las demás se pide al abrir cada cuenta
                for (MovimientoDto m : api.movimientos(cuentas.get(0).id(), 5)) {
                    ultimas.add(new OperacionResumida(m.fecha() == null ? null : m.fecha().toLocalDate(),
                            m.descripcion(), m.monto(), "DEBITO".equals(m.tipo()) ? "-" : "+"));
                }
            } catch (ServicioNoDisponibleException e) {
                parcial = true;
            }
        }
        String nombre;
        try {
            nombre = cliente.join().nombres().split(" ")[0];
        } catch (RuntimeException e) {
            nombre = null;
            parcial = true;
        }
        List<CuentaResumida> resumidas = cuentas.stream()
                .filter(c -> !"CERRADA".equals(c.estado()))
                .map(c -> new CuentaResumida(c.id(), c.tipo(), enmascarar(c.numero()), c.saldo()))
                .toList();
        BigDecimal total = resumidas.stream().map(CuentaResumida::saldo).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Inicio(nombre, total, resumidas, ultimas, parcial ? Boolean.TRUE : null);
    }

    @GetMapping("/cuentas/{cuentaId}/saldo")
    public Saldo saldo(@PathVariable Long cuentaId) {
        SaldoDto s = api.saldo(cuentaId);
        return new Saldo(s.saldo(), s.estado());
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos")
    public List<OperacionResumida> movimientos(@PathVariable Long cuentaId,
                                               @RequestParam(defaultValue = "15") int limite) {
        return api.movimientos(cuentaId, Math.min(limite, 30)).stream()
                .map(m -> new OperacionResumida(m.fecha() == null ? null : m.fecha().toLocalDate(),
                        m.descripcion(), m.monto(), "DEBITO".equals(m.tipo()) ? "-" : "+"))
                .toList();
    }

    @PostMapping("/transferencias")
    public ResponseEntity<ResultadoOperacion> transferir(@Valid @RequestBody TransferenciaMovilRequest req,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        String clave = key != null && !key.isBlank() ? "MOV-" + key : "MOV-" + UUID.randomUUID();
        PagoDto p = api.operar("transferencias",
                new OperacionRequest(req.origen(), req.destino(), req.monto(), req.glosa(), "MOVIL"), clave);
        String msg = switch (p.estado()) {
            case "COMPLETADO" -> "Transferencia realizada";
            case "PENDIENTE" -> "Transferencia en proceso, te avisaremos";
            default -> p.motivo();
        };
        HttpStatus status = switch (p.estado()) {
            case "COMPLETADO" -> HttpStatus.CREATED;
            case "PENDIENTE" -> HttpStatus.ACCEPTED;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(new ResultadoOperacion(p.referencia(), p.estado(), msg));
    }

    @GetMapping("/clientes/{clienteId}/avisos")
    public List<Aviso> avisos(@PathVariable Long clienteId) {
        return api.notificaciones(clienteId, 5).stream()
                .map(n -> new Aviso(n.mensaje(), n.fecha() == null ? null : n.fecha().toLocalDate()))
                .toList();
    }

    static String enmascarar(String numero) {
        if (numero == null || numero.length() < 4) {
            return "****";
        }
        return "****" + numero.substring(numero.length() - 4);
    }
}
