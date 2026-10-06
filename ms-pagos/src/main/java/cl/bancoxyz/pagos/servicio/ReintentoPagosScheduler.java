package cl.bancoxyz.pagos.servicio;

import cl.bancoxyz.pagos.dominio.EstadoPago;
import cl.bancoxyz.pagos.dominio.Pago;
import cl.bancoxyz.pagos.repositorio.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Retoma periódicamente los pagos que quedaron PENDIENTES porque ms-cuentas no respondía.
 * Si hay varias réplicas, el bloqueo optimista (@Version) evita que dos procesen el mismo pago.
 */
@Component
public class ReintentoPagosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReintentoPagosScheduler.class);

    private final PagoRepository pagos;
    private final PagoService servicio;

    public ReintentoPagosScheduler(PagoRepository pagos, PagoService servicio) {
        this.pagos = pagos;
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${pagos.reintento.intervalo-ms:30000}", initialDelay = 20000)
    public void reintentarPendientes() {
        List<Pago> pendientes = pagos.findTop50ByEstadoAndFechaActualizacionBeforeOrderByFechaCreacionAsc(
                EstadoPago.PENDIENTE, LocalDateTime.now().minusSeconds(20));
        if (pendientes.isEmpty()) {
            return;
        }
        log.info("Reintentando {} pago(s) pendiente(s)", pendientes.size());
        for (Pago p : pendientes) {
            try {
                servicio.ejecutar(p);
            } catch (OptimisticLockingFailureException e) {
                log.debug("Pago {} lo está procesando otra instancia", p.getReferencia());
            } catch (RuntimeException e) {
                log.error("Error reintentando pago {}: {}", p.getReferencia(), e.getMessage());
            }
        }
    }
}
