package cl.bancoxyz.batch.comun;

import org.springframework.batch.item.ItemCountAware;

/**
 * Base de los registros leídos desde CSV. Todos los campos se leen como texto para que un dato
 * malo no rompa la lectura: la validación y corrección se hace en el ItemProcessor.
 * El reader informa el número de ítem (ItemCountAware) para poder auditar la línea exacta.
 */
public abstract class RegistroCsv implements ItemCountAware {

    private int linea;

    @Override
    public void setItemCount(int count) {
        this.linea = count + 1; // +1 por la línea de encabezado
    }

    public int getLinea() {
        return linea;
    }

    /** Contenido original del registro (para auditoría). */
    public abstract String contenido();
}
