package cl.bancoxyz.batch.intereses;

import cl.bancoxyz.batch.comun.Numeros;
import cl.bancoxyz.batch.comun.RegistroInvalidoException;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reglas del Cálculo de Intereses Mensuales:
 *  - saldo obligatorio y >= 0; edad obligatoria entre 18 y 99; tipo: ahorro | prestamo | hipoteca.
 *  - registros EXACTAMENTE duplicados se omiten (se detectan con un hash SHA-256 del contenido).
 *  - interés = saldo x (tasa anual / 12), redondeado a 2 decimales (HALF_UP, igual que el COBOL).
 *    Para ahorro es interés ganado; para préstamo/hipoteca es interés cobrado sobre la deuda.
 * Antes de empezar carga los hashes ya guardados de esta instancia: si el paso se reinicia tras
 * una falla, los duplicados se siguen detectando correctamente.
 */
public class InteresProcessor implements ItemProcessor<CuentaInteresCsv, InteresMensual>, StepExecutionListener {

    static final int EDAD_MINIMA = 18;
    static final int EDAD_MAXIMA = 99;

    private final long instanciaId;
    private final String archivo;
    private final String periodo;
    private final Map<String, BigDecimal> tasaAnual;
    private final JdbcTemplate jdbc;
    private final Set<String> vistos = ConcurrentHashMap.newKeySet();

    public InteresProcessor(long instanciaId, String archivo, String periodo, Map<String, BigDecimal> tasaAnual,
                            JdbcTemplate jdbc) {
        this.instanciaId = instanciaId;
        this.archivo = archivo;
        this.periodo = periodo;
        this.tasaAnual = tasaAnual;
        this.jdbc = jdbc;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        vistos.addAll(jdbc.queryForList(
                "SELECT hash_registro FROM interes_mensual WHERE instancia_id = ? AND archivo = ?",
                String.class, instanciaId, archivo));
    }

    @Override
    public InteresMensual process(CuentaInteresCsv r) {
        long cuentaId = Numeros.entero(r.getCuentaId(), "cuenta_id");
        BigDecimal saldo = Numeros.monto(r.getSaldo(), "saldo");
        if (saldo.signum() < 0) {
            throw new RegistroInvalidoException("SALDO_NEGATIVO", "saldo negativo: " + saldo);
        }
        int edad = (int) Numeros.entero(r.getEdad(), "edad");
        if (edad < EDAD_MINIMA || edad > EDAD_MAXIMA) {
            throw new RegistroInvalidoException("EDAD_FUERA_DE_RANGO", "edad " + edad + " fuera de [18, 99]");
        }
        String tipo = r.getTipo() == null ? "" : r.getTipo().trim().toLowerCase();
        BigDecimal tasa = tasaAnual.get(tipo);
        if (tasa == null) {
            throw new RegistroInvalidoException("TIPO_PRODUCTO_INVALIDO", "tipo de producto desconocido: " + tipo);
        }
        String hash = hash(cuentaId + "|" + r.getNombre().trim() + "|" + saldo.toPlainString() + "|" + edad + "|" + tipo);
        if (!vistos.add(hash)) {
            throw new RegistroInvalidoException("REGISTRO_DUPLICADO", "registro idéntico ya procesado");
        }
        BigDecimal tasaMensual = tasa.divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
        BigDecimal interes = saldo.multiply(tasaMensual).setScale(2, RoundingMode.HALF_UP);
        return new InteresMensual(instanciaId, archivo, r.getLinea(), periodo, cuentaId, r.getNombre().trim(), tipo,
                edad, saldo.setScale(2, RoundingMode.HALF_UP), tasaMensual, interes,
                saldo.add(interes).setScale(2, RoundingMode.HALF_UP), hash);
    }

    private static String hash(String texto) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
