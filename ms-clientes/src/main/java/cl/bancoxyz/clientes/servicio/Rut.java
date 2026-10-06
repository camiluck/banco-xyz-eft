package cl.bancoxyz.clientes.servicio;

/** Validación de RUT chileno (módulo 11). */
public final class Rut {

    private Rut() {
    }

    /** Normaliza "12.345.678-5" -> "12345678-5". */
    public static String normalizar(String rut) {
        return rut == null ? null : rut.replace(".", "").replace(" ", "").toUpperCase();
    }

    public static boolean esValido(String rut) {
        String r = normalizar(rut);
        if (r == null || !r.matches("\\d{7,8}-[\\dK]")) {
            return false;
        }
        String cuerpo = r.substring(0, r.indexOf('-'));
        char dv = r.charAt(r.length() - 1);
        return calcularDv(cuerpo) == dv;
    }

    static char calcularDv(String cuerpo) {
        int suma = 0;
        int multiplicador = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(cuerpo.charAt(i)) * multiplicador;
            multiplicador = multiplicador == 7 ? 2 : multiplicador + 1;
        }
        int resto = 11 - (suma % 11);
        return resto == 11 ? '0' : resto == 10 ? 'K' : Character.forDigit(resto, 10);
    }
}
