package cl.bancoxyz.batch.comun;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * Normaliza las fechas del sistema legacy, que vienen en 4 formatos distintos:
 * yyyy-MM-dd (estándar), yyyy/MM/dd, dd-MM-yyyy y dd/MM/yyyy (formato chileno: día primero).
 * La validación es estricta: 2024-02-30 se rechaza.
 */
public final class FechaParser {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final List<DateTimeFormatter> ALTERNATIVOS = List.of(
            DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));

    private FechaParser() {
    }

    /** @param formatoCorregido true si la fecha venía en un formato no estándar y fue normalizada */
    public record Resultado(LocalDate fecha, boolean formatoCorregido) {
    }

    public static Resultado parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new RegistroInvalidoException("FECHA_VACIA", "la fecha es obligatoria");
        }
        String t = texto.trim();
        try {
            return new Resultado(LocalDate.parse(t, ISO), false);
        } catch (DateTimeParseException ignorada) {
            // se prueban los formatos alternativos
        }
        for (DateTimeFormatter f : ALTERNATIVOS) {
            try {
                return new Resultado(LocalDate.parse(t, f), true);
            } catch (DateTimeParseException ignorada) {
                // siguiente formato
            }
        }
        throw new RegistroInvalidoException("FECHA_INVALIDA", "formato de fecha no reconocido: " + t);
    }
}
