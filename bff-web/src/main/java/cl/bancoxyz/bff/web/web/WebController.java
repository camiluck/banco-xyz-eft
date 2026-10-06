package cl.bancoxyz.bff.web.web;

import cl.bancoxyz.bff.web.integracion.BancoApiClient;
import cl.bancoxyz.bff.web.integracion.Modelos.*;
import cl.bancoxyz.bff.web.integracion.ServicioNoDisponibleException;
import cl.bancoxyz.bff.web.web.DtosWeb.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * API del canal WEB (navegadores).
 * Optimización: el dashboard consulta clientes, cuentas, pagos y notificaciones EN PARALELO
 * (hilos virtuales), así el tiempo de respuesta es el de la llamada más lenta y no la suma.
 */
@RestController
@RequestMapping("/web")
public class WebController {

    private static final int MOVIMIENTOS_POR_CUENTA = 10;

    private final BancoApiClient api;
    private final ExecutorService paralelo = Executors.newVirtualThreadPerTaskExecutor();

    public WebController(BancoApiClient api) {
        this.api = api;
    }

    @GetMapping("/clientes/{clienteId}/resumen")
    public ResumenCliente resumen(@PathVariable Long clienteId) {
        List<String> noDisponibles = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<ClienteDto> cliente = async(() -> api.cliente(clienteId));
        CompletableFuture<List<NotificacionDto>> notificaciones =
                async(() -> seccion("notificaciones", noDisponibles, () -> api.notificaciones(clienteId, 10), List.of()));
        CompletableFuture<List<CuentaDto>> cuentas = async(() -> api.cuentasDeCliente(clienteId));

        // El perfil y las cuentas son esenciales: si fallan se informa el error (503) en vez de datos engañosos
        ClienteDto datosCliente = esperar(cliente);
        List<CuentaDto> listaCuentas = esperar(cuentas);

        // Por cada cuenta, movimientos y operaciones también en paralelo
        Map<Long, CompletableFuture<List<MovimientoDto>>> movs = new LinkedHashMap<>();
        Map<Long, CompletableFuture<List<PagoDto>>> ops = new LinkedHashMap<>();
        for (CuentaDto c : listaCuentas) {
            movs.put(c.id(), async(() -> seccion("movimientos", noDisponibles,
                    () -> api.movimientos(c.id(), MOVIMIENTOS_POR_CUENTA), List.of())));
            ops.put(c.id(), async(() -> seccion("operaciones", noDisponibles,
                    () -> api.operaciones(c.id(), 5), List.of())));
        }

        List<CuentaConMovimientos> detalle = listaCuentas.stream()
                .map(c -> new CuentaConMovimientos(c, esperar(movs.get(c.id()))))
                .toList();
        Map<String, PagoDto> operacionesUnicas = new LinkedHashMap<>();
        ops.values().forEach(f -> esperar(f).forEach(p -> operacionesUnicas.putIfAbsent(p.referencia(), p)));
        List<PagoDto> recientes = operacionesUnicas.values().stream()
                .sorted(Comparator.comparing(PagoDto::fechaCreacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10).toList();
        BigDecimal total = listaCuentas.stream().map(CuentaDto::saldo).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ResumenCliente(datosCliente, detalle, total, recientes, esperar(notificaciones),
                noDisponibles.stream().distinct().toList(), Instant.now());
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos")
    public List<MovimientoDto> movimientos(@PathVariable Long cuentaId, @RequestParam(defaultValue = "50") int limite) {
        return api.movimientos(cuentaId, limite);
    }

    @GetMapping("/cuentas/{cuentaId}/operaciones")
    public List<PagoDto> operaciones(@PathVariable Long cuentaId, @RequestParam(defaultValue = "50") int limite) {
        return api.operaciones(cuentaId, limite);
    }

    @PostMapping("/transferencias")
    public ResponseEntity<PagoDto> transferir(@Valid @RequestBody TransferenciaWebRequest req,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        PagoDto pago = api.operar("transferencias", new OperacionRequest(req.cuentaOrigenId(), req.cuentaDestinoId(),
                req.monto(), req.descripcion(), "WEB"), clave(key));
        return conEstado(pago);
    }

    @PostMapping("/pagos-servicios")
    public ResponseEntity<PagoDto> pagarServicio(@Valid @RequestBody PagoServicioWebRequest req,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        String descripcion = req.empresa() + " - cliente " + req.numeroCliente();
        PagoDto pago = api.operar("servicios",
                new OperacionRequest(req.cuentaOrigenId(), null, req.monto(), descripcion, "WEB"), clave(key));
        return conEstado(pago);
    }

    @PutMapping("/clientes/{clienteId}/perfil")
    public ClienteDto actualizarPerfil(@PathVariable Long clienteId, @Valid @RequestBody ActualizarPerfilRequest req) {
        Map<String, Object> cambios = new HashMap<>();
        if (req.email() != null) cambios.put("email", req.email());
        if (req.telefono() != null) cambios.put("telefono", req.telefono());
        if (req.direccion() != null) cambios.put("direccion", req.direccion());
        return api.actualizarCliente(clienteId, cambios);
    }

    // ------------------------------------------------------------------------------------------

    static ResponseEntity<PagoDto> conEstado(PagoDto pago) {
        HttpStatus status = switch (pago.estado()) {
            case "COMPLETADO" -> HttpStatus.CREATED;
            case "PENDIENTE" -> HttpStatus.ACCEPTED;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(pago);
    }

    private static String clave(String key) {
        return key != null && !key.isBlank() ? "WEB-" + key : "WEB-" + UUID.randomUUID();
    }

    private <T> CompletableFuture<T> async(Supplier<T> s) {
        return CompletableFuture.supplyAsync(s, paralelo);
    }

    /** Sección opcional del dashboard: si su servicio falla, se devuelve un valor por defecto. */
    private static <T> T seccion(String nombre, List<String> noDisponibles, Supplier<T> s, T porDefecto) {
        try {
            return s.get();
        } catch (ServicioNoDisponibleException e) {
            noDisponibles.add(nombre);
            return porDefecto;
        }
    }

    private static <T> T esperar(CompletableFuture<T> f) {
        try {
            return f.join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
