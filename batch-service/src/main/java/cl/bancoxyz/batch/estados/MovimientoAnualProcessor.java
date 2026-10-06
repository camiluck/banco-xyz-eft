package cl.bancoxyz.batch.estados;

import cl.bancoxyz.batch.comun.FechaParser;
import cl.bancoxyz.batch.comun.Numeros;
import cl.bancoxyz.batch.comun.RegistroInvalidoException;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Set;

/**
 * Reglas de limpieza para los Estados de Cuenta Anuales:
 *  - transacción normalizada (minúsculas y sin tildes: "depósito" -> "deposito");
 *    válidas: deposito, retiro, compra, pago.
 *  - fecha normalizada; los movimientos de otro año se FILTRAN (no se consideran en este estado).
 *  - monto vacío o 0 -> se omite.
 *  - monto negativo en retiro/compra/pago: el legacy guardaba los cargos con signo negativo,
 *    se toma el valor absoluto y se marca como "ajustado". Negativo en un depósito -> se omite.
 *  - descripción vacía -> "SIN DESCRIPCION" (no es motivo de rechazo).
 */
public class MovimientoAnualProcessor implements ItemProcessor<MovimientoAnualCsv, MovimientoAnual> {

    static final Set<String> CARGOS = Set.of("retiro", "compra", "pago");

    private final long instanciaId;
    private final String archivo;
    private final int anio;

    public MovimientoAnualProcessor(long instanciaId, String archivo, int anio) {
        this.instanciaId = instanciaId;
        this.archivo = archivo;
        this.anio = anio;
    }

    @Override
    public MovimientoAnual process(MovimientoAnualCsv r) {
        long cuentaId = Numeros.entero(r.getCuentaId(), "cuenta_id");
        FechaParser.Resultado fecha = FechaParser.parsear(r.getFecha());
        if (fecha.fecha().getYear() != anio) {
            return null; // filtrado: pertenece a otro período
        }
        String transaccion = normalizar(r.getTransaccion());
        if (!transaccion.equals("deposito") && !CARGOS.contains(transaccion)) {
            throw new RegistroInvalidoException("TRANSACCION_INVALIDA", "tipo de transacción desconocido: " + transaccion);
        }
        BigDecimal monto = Numeros.monto(r.getMonto(), "monto");
        if (monto.signum() == 0) {
            throw new RegistroInvalidoException("MONTO_CERO", "movimiento sin monto");
        }
        boolean ajustado = false;
        if (monto.signum() < 0) {
            if (!CARGOS.contains(transaccion)) {
                throw new RegistroInvalidoException("MONTO_NEGATIVO_EN_DEPOSITO", "depósito con monto negativo: " + monto);
            }
            monto = monto.abs();
            ajustado = true;
        }
        String descripcion = r.getDescripcion() == null || r.getDescripcion().isBlank()
                ? "SIN DESCRIPCION" : r.getDescripcion().trim();
        return new MovimientoAnual(instanciaId, archivo, r.getLinea(), cuentaId, fecha.fecha(), anio, transaccion,
                monto, r.getMonto().trim(), descripcion, ajustado);
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes;
    }
}
