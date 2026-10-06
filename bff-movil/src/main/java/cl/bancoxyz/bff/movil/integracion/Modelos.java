package cl.bancoxyz.bff.movil.integracion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Representación de las respuestas de los microservicios internos (sólo los campos que este BFF usa). */
public final class Modelos {

    private Modelos() {
    }

    public record ClienteDto(Long id, String rut, String nombres, String apellidos, String email, String telefono,
                             String direccion, String segmento, boolean activo, int cuentasActivas) {
    }

    public record CuentaDto(Long id, String numero, Long clienteId, String tipo, BigDecimal saldo, String estado,
                            LocalDateTime fechaApertura) {
    }

    public record SaldoDto(Long cuentaId, String numero, BigDecimal saldo, String estado) {
    }

    public record MovimientoDto(Long id, Long cuentaId, String tipo, BigDecimal monto, BigDecimal saldoResultante,
                                String referencia, String descripcion, LocalDateTime fecha) {
    }

    public record PagoDto(String referencia, String tipo, String estado, BigDecimal monto, Long cuentaOrigenId,
                          Long cuentaDestinoId, String descripcion, String canal, String motivo,
                          LocalDateTime fechaCreacion) {
    }

    public record NotificacionDto(Long id, String tipo, String nivel, String mensaje, LocalDateTime fecha) {
    }

    public record PinDto(boolean valido, int intentosRestantes, boolean bloqueada) {
    }

    public record OperacionRequest(Long cuentaOrigenId, Long cuentaDestinoId, BigDecimal monto, String descripcion,
                                   String canal) {
    }
}
