package cl.bancoxyz.bff.movil.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Respuestas del canal MÓVIL: sólo lo esencial, nombres cortos y sin campos nulos
 * para reducir el consumo de datos (además se comprime con gzip y se usa ETag).
 */
public final class DtosMovil {

    private DtosMovil() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Inicio(String nombre, BigDecimal saldoTotal, List<CuentaResumida> cuentas,
                         List<OperacionResumida> ultimas, Boolean datosParciales) {
    }

    /** "num" va enmascarado: en el celular sólo se muestran los últimos 4 dígitos. */
    public record CuentaResumida(Long id, String tipo, String num, BigDecimal saldo) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OperacionResumida(LocalDate f, String desc, BigDecimal monto, String signo) {
    }

    public record Saldo(BigDecimal saldo, String estado) {
    }

    public record TransferenciaMovilRequest(@NotNull Long origen, @NotNull Long destino,
                                            @NotNull @Positive BigDecimal monto, @Size(max = 60) String glosa) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResultadoOperacion(String ref, String estado, String msg) {
    }

    public record Aviso(String msg, LocalDate f) {
    }
}
