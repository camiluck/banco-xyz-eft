package cl.bancoxyz.batch.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Auditoría de registros omitidos: cada registro rechazado queda guardado en la tabla
 * registro_rechazado con el archivo, la línea, el contenido original y el motivo.
 * Así nada se pierde "en silencio" y se puede corregir y reprocesar.
 */
public class RegistroRechazoListener implements SkipListener<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(RegistroRechazoListener.class);

    private final JdbcTemplate jdbc;
    private final long instanciaId;
    private final String job;
    private final String archivo;

    public RegistroRechazoListener(JdbcTemplate jdbc, long instanciaId, String job, String archivo) {
        this.jdbc = jdbc;
        this.instanciaId = instanciaId;
        this.job = job;
        this.archivo = archivo;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof FlatFileParseException e) {
            guardar(e.getLineNumber(), e.getInput(), "LINEA_MAL_FORMADA: " + raiz(e));
        } else {
            guardar(null, null, raiz(t));
        }
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        Integer linea = item instanceof RegistroCsv r ? r.getLinea() : null;
        String contenido = item instanceof RegistroCsv r ? r.contenido() : String.valueOf(item);
        guardar(linea, contenido, t.getMessage());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        guardar(null, String.valueOf(item), "ERROR_ESCRITURA: " + t.getMessage());
    }

    private void guardar(Integer linea, String contenido, String motivo) {
        log.debug("Registro omitido [{}:{}] {} -> {}", archivo, linea, contenido, motivo);
        jdbc.update("""
                INSERT INTO registro_rechazado (instancia_id, job, archivo, linea, contenido, motivo, fecha)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                instanciaId, job, archivo, linea, recortar(contenido, 500), recortar(motivo, 300),
                Timestamp.valueOf(LocalDateTime.now()));
    }

    private static String raiz(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r.getClass().getSimpleName() + " - " + r.getMessage();
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
