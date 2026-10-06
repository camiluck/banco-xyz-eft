package cl.bancoxyz.bff.atm.web;

import cl.bancoxyz.bff.atm.integracion.BancoApiClient;
import cl.bancoxyz.bff.atm.integracion.Modelos.*;
import cl.bancoxyz.bff.atm.integracion.ServicioNoDisponibleException;
import cl.bancoxyz.bff.atm.seguridad.PublicadorAlertas;
import cl.bancoxyz.bff.atm.seguridad.TerminalAutorizadoFilter;
import cl.bancoxyz.bff.atm.web.DtosAtm.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * API del canal CAJEROS AUTOMÁTICOS: operaciones críticas (consulta de saldo y retiro).
 *  - Cada operación valida el PIN (bloqueo tras 3 intentos en ms-cuentas).
 *  - El retiro exige Idempotency-Key: si el cajero pierde la conexión y reintenta, no se descuenta dos veces.
 *  - Respuestas con Cache-Control: no-store (nada sensible queda en caché).
 */
@RestController
@RequestMapping("/atm")
public class AtmController {

    private static final Logger log = LoggerFactory.getLogger(AtmController.class);

    private final BancoApiClient api;
    private final PublicadorAlertas alertas;
    private final BigDecimal retiroMaximo;
    private final BigDecimal multiplo;

    public AtmController(BancoApiClient api, PublicadorAlertas alertas,
                         @Value("${atm.retiro.maximo:200000}") BigDecimal retiroMaximo,
                         @Value("${atm.retiro.multiplo:5000}") BigDecimal multiplo) {
        this.api = api;
        this.alertas = alertas;
        this.retiroMaximo = retiroMaximo;
        this.multiplo = multiplo;
    }

    @PostMapping("/consulta-saldo")
    public ResponseEntity<SaldoAtm> consultarSaldo(@Valid @RequestBody ConsultaSaldoRequest req,
                                                   @RequestHeader(TerminalAutorizadoFilter.CABECERA) String terminal) {
        verificarPin(req.cuentaId(), req.pin(), terminal);
        SaldoDto saldo = api.saldo(req.cuentaId());
        return sinCache(HttpStatus.OK, new SaldoAtm(enmascarar(saldo.numero()), saldo.saldo()));
    }

    @PostMapping("/retiros")
    public ResponseEntity<RetiroAtm> retirar(@Valid @RequestBody RetiroRequest req,
                                             @RequestHeader(TerminalAutorizadoFilter.CABECERA) String terminal,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (req.monto().compareTo(retiroMaximo) > 0) {
            throw new ReglaCanalException("MONTO_EXCEDE_LIMITE", "El monto máximo por retiro es $" + retiroMaximo,
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (req.monto().remainder(multiplo).signum() != 0) {
            throw new ReglaCanalException("MONTO_NO_DISPENSABLE",
                    "El monto debe ser múltiplo de $" + multiplo + " (billetes disponibles)", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        verificarPin(req.cuentaId(), req.pin(), terminal);

        PagoDto retiro = api.operar("retiros", new OperacionRequest(req.cuentaId(), null, req.monto(),
                "Cajero " + terminal, "ATM"), "ATM-" + terminal + "-" + idempotencyKey);
        log.info("Retiro {} en terminal {}: {}", retiro.referencia(), terminal, retiro.estado());

        BigDecimal saldo = null;
        try {
            saldo = api.saldo(req.cuentaId()).saldo();
        } catch (ServicioNoDisponibleException e) {
            // El saldo es informativo: si no se puede consultar, el comprobante se emite igual
        }
        return switch (retiro.estado()) {
            case "COMPLETADO" -> sinCache(HttpStatus.CREATED, new RetiroAtm(retiro.referencia(), retiro.estado(),
                    retiro.monto(), saldo, "Retire su dinero"));
            case "PENDIENTE" -> sinCache(HttpStatus.ACCEPTED, new RetiroAtm(retiro.referencia(), retiro.estado(),
                    BigDecimal.ZERO, saldo, "Operación en proceso. No se entregará dinero hasta confirmar."));
            default -> sinCache(HttpStatus.UNPROCESSABLE_ENTITY, new RetiroAtm(retiro.referencia(), retiro.estado(),
                    BigDecimal.ZERO, saldo, "Operación rechazada: " + retiro.motivo()));
        };
    }

    private void verificarPin(Long cuentaId, String pin, String terminal) {
        PinDto resultado = api.validarPin(cuentaId, pin);
        if (resultado.valido()) {
            return;
        }
        if (resultado.bloqueada()) {
            throw new ReglaCanalException("TARJETA_BLOQUEADA",
                    "Tarjeta bloqueada. Comuníquese con el banco.", HttpStatus.LOCKED);
        }
        alertas.publicar("PIN_INCORRECTO", "BAJO", null, cuentaId,
                "PIN incorrecto en terminal " + terminal + ". Intentos restantes: " + resultado.intentosRestantes());
        throw new ReglaCanalException("PIN_INCORRECTO",
                "PIN incorrecto. Intentos restantes: " + resultado.intentosRestantes(), HttpStatus.UNAUTHORIZED);
    }

    private static <T> ResponseEntity<T> sinCache(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    private static String enmascarar(String numero) {
        return numero == null || numero.length() < 4 ? "****" : "****" + numero.substring(numero.length() - 4);
    }
}
