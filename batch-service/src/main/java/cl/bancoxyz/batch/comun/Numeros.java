package cl.bancoxyz.batch.comun;

import java.math.BigDecimal;

public final class Numeros {

    private Numeros() {
    }

    public static BigDecimal monto(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            throw new RegistroInvalidoException(campo.toUpperCase() + "_VACIO", "el campo " + campo + " está vacío");
        }
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            throw new RegistroInvalidoException(campo.toUpperCase() + "_NO_NUMERICO", campo + " no numérico: " + texto);
        }
    }

    public static long entero(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            throw new RegistroInvalidoException(campo.toUpperCase() + "_VACIO", "el campo " + campo + " está vacío");
        }
        try {
            return Long.parseLong(texto.trim());
        } catch (NumberFormatException e) {
            throw new RegistroInvalidoException(campo.toUpperCase() + "_NO_NUMERICO", campo + " no numérico: " + texto);
        }
    }
}
