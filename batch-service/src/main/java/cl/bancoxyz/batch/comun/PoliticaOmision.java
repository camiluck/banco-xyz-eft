package cl.bancoxyz.batch.comun;

import cl.bancoxyz.batch.config.BatchProperties;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.stereotype.Component;

/**
 * Política de omisión (skip):
 *  - Datos inválidos (RegistroInvalidoException) o líneas mal formadas (FlatFileParseException)
 *    se omiten y se registran, hasta un máximo configurable por paso.
 *  - Cualquier otro error (BD caída, disco lleno, bug) NO se omite: el paso falla y el job
 *    queda FAILED para ser reejecutado desde el último commit.
 */
@Component
public class PoliticaOmision implements SkipPolicy {

    private final int maxOmisiones;

    public PoliticaOmision(BatchProperties props) {
        this.maxOmisiones = props.maxOmisiones();
    }

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        boolean esErrorDeDatos = t instanceof RegistroInvalidoException || t instanceof FlatFileParseException;
        if (!esErrorDeDatos) {
            return false;
        }
        if (skipCount >= maxOmisiones) {
            throw new SkipLimitExceededException(maxOmisiones, t);
        }
        return true;
    }
}
