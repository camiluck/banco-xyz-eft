package cl.bancoxyz.bff.web.integracion;

/** Un servicio interno no respondió (caído, timeout o circuito abierto). */
public class ServicioNoDisponibleException extends RuntimeException {

    private final String servicio;

    public ServicioNoDisponibleException(String servicio, Throwable causa) {
        super("Servicio " + servicio + " no disponible", causa);
        this.servicio = servicio;
    }

    public String getServicio() {
        return servicio;
    }
}
