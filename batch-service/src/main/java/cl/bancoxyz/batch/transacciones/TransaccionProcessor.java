package cl.bancoxyz.batch.transacciones;

import cl.bancoxyz.batch.comun.FechaParser;
import cl.bancoxyz.batch.comun.Numeros;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reglas del Reporte de Transacciones Diarias (equivalentes al programa COBOL):
 *  - id, fecha y monto son obligatorios y deben ser válidos -> si no, el registro se OMITE.
 *  - fechas en formato no estándar se NORMALIZAN a yyyy-MM-dd.
 *  - monto negativo, monto cero o tipo distinto de debito/credito -> se marca como ANOMALÍA
 *    (se guarda para revisión, pero no se suma en los totales del día).
 */
public class TransaccionProcessor implements ItemProcessor<MovimientoDiarioCsv, TransaccionDiaria> {

    static final Set<String> TIPOS_VALIDOS = Set.of("debito", "credito");

    private final long instanciaId;
    private final String archivo;

    public TransaccionProcessor(long instanciaId, String archivo) {
        this.instanciaId = instanciaId;
        this.archivo = archivo;
    }

    @Override
    public TransaccionDiaria process(MovimientoDiarioCsv r) {
        long id = Numeros.entero(r.getId(), "id");
        FechaParser.Resultado fecha = FechaParser.parsear(r.getFecha());
        BigDecimal monto = Numeros.monto(r.getMonto(), "monto");
        String tipo = r.getTipo() == null ? "" : r.getTipo().trim().toLowerCase();

        List<String> anomalias = new ArrayList<>();
        if (monto.signum() < 0) {
            anomalias.add("MONTO_NEGATIVO");
        } else if (monto.signum() == 0) {
            anomalias.add("MONTO_CERO");
        }
        if (!TIPOS_VALIDOS.contains(tipo)) {
            anomalias.add("TIPO_INVALIDO(" + (tipo.isEmpty() ? "vacio" : tipo) + ")");
        }
        return new TransaccionDiaria(instanciaId, archivo, r.getLinea(), id, fecha.fecha(), monto, tipo,
                r.getFecha().trim(), fecha.formatoCorregido(), !anomalias.isEmpty(),
                anomalias.isEmpty() ? null : String.join(",", anomalias));
    }
}
