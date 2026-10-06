package cl.bancoxyz.cuentas.servicio;

import cl.bancoxyz.cuentas.dominio.*;
import cl.bancoxyz.cuentas.eventos.Eventos;
import cl.bancoxyz.cuentas.repositorio.CuentaRepository;
import cl.bancoxyz.cuentas.repositorio.MovimientoRepository;
import cl.bancoxyz.cuentas.web.Dtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CuentaService {

    private static final Logger log = LoggerFactory.getLogger(CuentaService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CuentaRepository cuentas;
    private final MovimientoRepository movimientos;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;
    private final int intentosMaximosPin;

    public CuentaService(CuentaRepository cuentas, MovimientoRepository movimientos, PasswordEncoder passwordEncoder,
                         ApplicationEventPublisher eventos,
                         @Value("${cuentas.pin.intentos-maximos:3}") int intentosMaximosPin) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
        this.intentosMaximosPin = intentosMaximosPin;
    }

    // ------------------------------------------------------------------ apertura / consulta

    @Transactional
    public Cuenta abrir(AbrirCuentaRequest req) {
        String numero = "XYZ-%04d-%08d".formatted(req.clienteId() % 10000, RANDOM.nextInt(100_000_000));
        Cuenta cuenta = cuentas.save(new Cuenta(numero, req.clienteId(), req.tipo(),
                req.depositoInicial(), passwordEncoder.encode(req.pin())));
        if (req.depositoInicial().signum() > 0) {
            movimientos.save(new Movimiento(cuenta.getId(), TipoMovimiento.CREDITO, req.depositoInicial(),
                    req.depositoInicial(), "APERTURA-" + numero, "Depósito inicial"));
        }
        log.info("Cuenta {} abierta para cliente {}", numero, req.clienteId());
        publicarEventoCuenta("CUENTA_ABIERTA", cuenta);
        return cuenta;
    }

    @Transactional(readOnly = true)
    public Cuenta obtener(Long id) {
        return cuentas.findById(id).orElseThrow(() -> ReglaNegocioException.noEncontrada(id));
    }

    @Transactional(readOnly = true)
    public List<Cuenta> listarPorCliente(Long clienteId) {
        return cuentas.findByClienteIdOrderByIdAsc(clienteId);
    }

    @Transactional(readOnly = true)
    public List<Movimiento> ultimosMovimientos(Long cuentaId, int limite) {
        obtener(cuentaId);
        return movimientos.findByCuentaIdOrderByFechaDescIdDesc(cuentaId, PageRequest.of(0, Math.min(limite, 100)));
    }

    // ------------------------------------------------------------------ mantenimiento / cierre

    @Transactional
    public Cuenta cerrar(Long id) {
        Cuenta cuenta = cuentas.buscarParaActualizar(id).orElseThrow(() -> ReglaNegocioException.noEncontrada(id));
        if (cuenta.getEstado() == EstadoCuenta.CERRADA) {
            throw new ReglaNegocioException("CUENTA_YA_CERRADA", "La cuenta ya está cerrada", HttpStatus.CONFLICT);
        }
        if (cuenta.getSaldo().signum() != 0) {
            throw new ReglaNegocioException("SALDO_DISTINTO_DE_CERO",
                    "Para cerrar la cuenta el saldo debe ser 0 (saldo actual: " + cuenta.getSaldo() + ")",
                    HttpStatus.CONFLICT);
        }
        cuenta.setEstado(EstadoCuenta.CERRADA);
        cuenta.setFechaCierre(LocalDateTime.now());
        publicarEventoCuenta("CUENTA_CERRADA", cuenta);
        return cuenta;
    }

    @Transactional
    public Cuenta cambiarEstado(Long id, EstadoCuenta nuevoEstado) {
        if (nuevoEstado == EstadoCuenta.CERRADA) {
            return cerrar(id);
        }
        Cuenta cuenta = cuentas.buscarParaActualizar(id).orElseThrow(() -> ReglaNegocioException.noEncontrada(id));
        if (cuenta.getEstado() == EstadoCuenta.CERRADA) {
            throw new ReglaNegocioException("CUENTA_CERRADA", "Una cuenta cerrada no se puede reactivar", HttpStatus.CONFLICT);
        }
        cuenta.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoCuenta.ACTIVA) {
            cuenta.setIntentosFallidosPin(0);
        }
        publicarEventoCuenta(nuevoEstado == EstadoCuenta.ACTIVA ? "CUENTA_ACTIVADA" : "CUENTA_BLOQUEADA", cuenta);
        return cuenta;
    }

    // ------------------------------------------------------------------ movimientos (débito / crédito)

    /**
     * Aplica un débito o crédito. Es IDEMPOTENTE: si la referencia ya existe se devuelve el movimiento
     * original sin volver a tocar el saldo. Esto permite que ms-pagos reintente sin duplicar dinero.
     */
    @Transactional
    public MovimientoResponse registrarMovimiento(Long cuentaId, MovimientoRequest req) {
        Optional<Movimiento> existente = movimientos.findByReferencia(req.referencia());
        if (existente.isPresent()) {
            Movimiento m = existente.get();
            if (!m.getCuentaId().equals(cuentaId)) {
                throw new ReglaNegocioException("REFERENCIA_DUPLICADA",
                        "La referencia ya fue usada en otra cuenta", HttpStatus.CONFLICT);
            }
            log.info("Movimiento {} ya aplicado anteriormente (reintento idempotente)", req.referencia());
            return MovimientoResponse.de(m, obtener(cuentaId).getClienteId(), true);
        }

        Cuenta cuenta = cuentas.buscarParaActualizar(cuentaId)
                .orElseThrow(() -> ReglaNegocioException.noEncontrada(cuentaId));
        if (!cuenta.estaActiva()) {
            throw new ReglaNegocioException("CUENTA_NO_ACTIVA",
                    "La cuenta " + cuenta.getNumero() + " está " + cuenta.getEstado(), HttpStatus.CONFLICT);
        }

        BigDecimal nuevoSaldo;
        if (req.tipo() == TipoMovimiento.DEBITO) {
            if (cuenta.getSaldo().compareTo(req.monto()) < 0) {
                throw new ReglaNegocioException("SALDO_INSUFICIENTE", "Saldo insuficiente", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            nuevoSaldo = cuenta.getSaldo().subtract(req.monto());
        } else {
            nuevoSaldo = cuenta.getSaldo().add(req.monto());
        }
        cuenta.setSaldo(nuevoSaldo);
        Movimiento mov = movimientos.save(new Movimiento(cuentaId, req.tipo(), req.monto(), nuevoSaldo,
                req.referencia(), req.descripcion()));
        log.info("Movimiento {} {} por {} en cuenta {}. Nuevo saldo {}", req.referencia(), req.tipo(),
                req.monto(), cuenta.getNumero(), nuevoSaldo);
        return MovimientoResponse.de(mov, cuenta.getClienteId(), false);
    }

    // ------------------------------------------------------------------ PIN (cajeros automáticos)

    @Transactional
    public ValidarPinResponse validarPin(Long cuentaId, String pin) {
        Cuenta cuenta = cuentas.buscarParaActualizar(cuentaId)
                .orElseThrow(() -> ReglaNegocioException.noEncontrada(cuentaId));
        if (cuenta.getEstado() != EstadoCuenta.ACTIVA) {
            return new ValidarPinResponse(false, 0, true);
        }
        if (cuenta.getPinHash() != null && passwordEncoder.matches(pin, cuenta.getPinHash())) {
            cuenta.setIntentosFallidosPin(0);
            return new ValidarPinResponse(true, intentosMaximosPin, false);
        }
        int intentos = cuenta.getIntentosFallidosPin() + 1;
        cuenta.setIntentosFallidosPin(intentos);
        if (intentos >= intentosMaximosPin) {
            cuenta.setEstado(EstadoCuenta.BLOQUEADA);
            log.warn("Cuenta {} bloqueada por {} intentos fallidos de PIN", cuenta.getNumero(), intentos);
            publicarEventoCuenta("CUENTA_BLOQUEADA", cuenta);
            eventos.publishEvent(new Eventos.AlertaSeguridadEvento("PIN_BLOQUEADO", "ALTO", cuenta.getClienteId(),
                    cuenta.getId(), "ms-cuentas",
                    "Cuenta bloqueada tras " + intentos + " intentos fallidos de PIN", Instant.now()));
            return new ValidarPinResponse(false, 0, true);
        }
        return new ValidarPinResponse(false, intentosMaximosPin - intentos, false);
    }

    private void publicarEventoCuenta(String tipo, Cuenta c) {
        eventos.publishEvent(new Eventos.CuentaEvento(tipo, c.getId(), c.getNumero(), c.getClienteId(), Instant.now()));
    }
}
