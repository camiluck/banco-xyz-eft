package cl.bancoxyz.pagos.web;

import cl.bancoxyz.pagos.dominio.EstadoPago;
import cl.bancoxyz.pagos.dominio.Pago;
import cl.bancoxyz.pagos.dominio.TipoPago;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Dtos {

    private Dtos() {
    }

    /** Request común para transferencias, pagos de servicio, depósitos y retiros. */
    public record OperacionRequest(
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            @NotNull @Positive @DecimalMax(value = "50000000", message = "Monto máximo por operación: 50.000.000") BigDecimal monto,
            @Size(max = 150) String descripcion,
            @Size(max = 10) String canal) {
    }

    public record PagoResponse(String referencia, TipoPago tipo, EstadoPago estado, BigDecimal monto,
                               Long cuentaOrigenId, Long cuentaDestinoId, String descripcion, String canal,
                               String motivo, int intentos, LocalDateTime fechaCreacion,
                               LocalDateTime fechaActualizacion) {
        public static PagoResponse de(Pago p) {
            return new PagoResponse(p.getReferencia(), p.getTipo(), p.getEstado(), p.getMonto(), p.getCuentaOrigenId(),
                    p.getCuentaDestinoId(), p.getDescripcion(), p.getCanal(), p.getMotivo(), p.getIntentos(),
                    p.getFechaCreacion(), p.getFechaActualizacion());
        }
    }
}
