package cl.bancoxyz.bff.atm.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Mensajes mínimos del canal CAJEROS: sin datos personales, sólo lo necesario para la operación. */
public final class DtosAtm {

    private DtosAtm() {
    }

    public record ConsultaSaldoRequest(@NotNull Long cuentaId, @NotNull @Pattern(regexp = "\\d{4}") String pin) {
    }

    public record RetiroRequest(@NotNull Long cuentaId, @NotNull @Pattern(regexp = "\\d{4}") String pin,
                                @NotNull @Positive BigDecimal monto) {
    }

    public record SaldoAtm(String cuenta, BigDecimal saldoDisponible) {
    }

    public record RetiroAtm(String comprobante, String estado, BigDecimal montoEntregado, BigDecimal saldoDisponible,
                            String mensaje) {
    }
}
