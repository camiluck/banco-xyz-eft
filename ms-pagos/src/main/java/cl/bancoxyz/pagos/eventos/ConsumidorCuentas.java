package cl.bancoxyz.pagos.eventos;

import cl.bancoxyz.pagos.dominio.EstadoPago;
import cl.bancoxyz.pagos.dominio.Pago;
import cl.bancoxyz.pagos.repositorio.PagoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de "cuentas-eventos": si una cuenta se cierra o bloquea, los pagos pendientes que
 * aún no debitaron desde ella se rechazan de inmediato (consistencia entre servicios).
 */
@Component
public class ConsumidorCuentas {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorCuentas.class);

    private final PagoRepository pagos;
    private final ObjectMapper mapper;

    public ConsumidorCuentas(PagoRepository pagos, ObjectMapper mapper) {
        this.pagos = pagos;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Eventos.TOPICO_CUENTAS, autoStartup = "${app.kafka.listeners:true}")
    @Transactional
    public void onEventoCuenta(String json) throws JsonProcessingException {
        JsonNode e = mapper.readTree(json);
        String tipo = e.path("tipo").asText();
        if (!"CUENTA_CERRADA".equals(tipo) && !"CUENTA_BLOQUEADA".equals(tipo)) {
            return;
        }
        long cuentaId = e.path("cuentaId").asLong();
        for (Pago p : pagos.findByEstadoAndCuentaOrigenIdAndDebitoAplicadoFalse(EstadoPago.PENDIENTE, cuentaId)) {
            p.rechazar("Cuenta origen " + (tipo.equals("CUENTA_CERRADA") ? "cerrada" : "bloqueada"));
            log.info("Pago {} rechazado por evento {} de la cuenta {}", p.getReferencia(), tipo, cuentaId);
        }
    }
}
