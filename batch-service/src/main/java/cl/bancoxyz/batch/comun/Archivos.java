package cl.bancoxyz.batch.comun;

/** Utilidades para nombrar los archivos de entrada en los registros de auditoría. */
public final class Archivos {

    private Archivos() {
    }

    /** "file:/app/data/semana_3/movimientos.csv" -> "semana_3/movimientos.csv" */
    public static String etiqueta(String url) {
        if (url == null) {
            return "desconocido";
        }
        String limpio = url.replace('\\', '/');
        int ultimo = limpio.lastIndexOf('/');
        int penultimo = ultimo > 0 ? limpio.lastIndexOf('/', ultimo - 1) : -1;
        return penultimo >= 0 ? limpio.substring(penultimo + 1) : limpio;
    }
}
