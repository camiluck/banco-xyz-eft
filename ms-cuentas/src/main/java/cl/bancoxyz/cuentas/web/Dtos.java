package cl.bancoxyz.cuentas.web;

import cl.bancoxyz.cuentas.dominio.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Objetos de entrada/salida de la API REST de cuentas. */
public final class Dtos {

    private Dtos() {
    }

    public record AbrirCuentaRequest(
            @NotNull Long clienteId,
            @NotNull TipoCuenta tipo,
            @NotNull @PositiveOrZero BigDecimal depositoInicial,
            @NotBlank @Pattern(regexp = "\\d{4}", message = "El PIN debe tener 4 dígitos") String pin) {
    }

    public record CambiarEstadoRequest(@NotNull EstadoCuenta estado) {
    }

    public record MovimientoRequest(
            @NotNull TipoMovimiento tipo,
            @NotNull @Positive BigDecimal monto,
            @NotBlank @Size(max = 80) String referencia,
            @Size(max = 200) String descripcion) {
    }

    public record ValidarPinRequest(@NotBlank @Pattern(regexp = "\\d{4}") String pin) {
    }

    public record CuentaResponse(Long id, String numero, Long clienteId, TipoCuenta tipo, BigDecimal saldo,
                                 EstadoCuenta estado, LocalDateTime fechaApertura, LocalDateTime fechaCierre) {
        public static CuentaResponse de(Cuenta c) {
            return new CuentaResponse(c.getId(), c.getNumero(), c.getClienteId(), c.getTipo(), c.getSaldo(),
                    c.getEstado(), c.getFechaApertura(), c.getFechaCierre());
        }
    }

    public record SaldoResponse(Long cuentaId, String numero, BigDecimal saldo, EstadoCuenta estado) {
    }

    public record MovimientoResponse(Long id, Long cuentaId, Long clienteId, TipoMovimiento tipo, BigDecimal monto,
                                     BigDecimal saldoResultante, String referencia, String descripcion,
                                     LocalDateTime fecha, boolean duplicado) {
        public static MovimientoResponse de(Movimiento m, Long clienteId, boolean duplicado) {
            return new MovimientoResponse(m.getId(), m.getCuentaId(), clienteId, m.getTipo(), m.getMonto(),
                    m.getSaldoResultante(), m.getReferencia(), m.getDescripcion(), m.getFecha(), duplicado);
        }
    }

    public record ValidarPinResponse(boolean valido, int intentosRestantes, boolean bloqueada) {
    }
}
