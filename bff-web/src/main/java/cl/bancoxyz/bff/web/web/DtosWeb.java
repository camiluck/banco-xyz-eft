package cl.bancoxyz.bff.web.web;

import cl.bancoxyz.bff.web.integracion.Modelos.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Respuestas del canal WEB: datos COMPLETOS para interfaces ricas (dashboard con varias secciones,
 * tablas de movimientos, perfil completo).
 */
public final class DtosWeb {

    private DtosWeb() {
    }

    public record CuentaConMovimientos(CuentaDto cuenta, List<MovimientoDto> ultimosMovimientos) {
    }

    /** Dashboard completo. Si una sección falla, se informa en seccionesNoDisponibles (degradación elegante). */
    public record ResumenCliente(ClienteDto cliente,
                                 List<CuentaConMovimientos> cuentas,
                                 BigDecimal saldoTotal,
                                 List<PagoDto> operacionesRecientes,
                                 List<NotificacionDto> notificaciones,
                                 List<String> seccionesNoDisponibles,
                                 Instant generadoEn) {
    }

    public record TransferenciaWebRequest(@NotNull Long cuentaOrigenId,
                                          @NotNull Long cuentaDestinoId,
                                          @NotNull @Positive BigDecimal monto,
                                          @Size(max = 150) String descripcion) {
    }

    public record PagoServicioWebRequest(@NotNull Long cuentaOrigenId,
                                         @NotNull @Positive BigDecimal monto,
                                         @NotBlank @Size(max = 60) String empresa,
                                         @NotBlank @Size(max = 40) String numeroCliente) {
    }

    public record ActualizarPerfilRequest(@Email String email, @Size(max = 20) String telefono,
                                          @Size(max = 200) String direccion) {
    }
}
