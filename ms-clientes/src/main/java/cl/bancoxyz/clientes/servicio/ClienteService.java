package cl.bancoxyz.clientes.servicio;

import cl.bancoxyz.clientes.dominio.Cliente;
import cl.bancoxyz.clientes.dominio.Notificacion;
import cl.bancoxyz.clientes.dominio.Segmento;
import cl.bancoxyz.clientes.repositorio.ClienteRepository;
import cl.bancoxyz.clientes.repositorio.NotificacionRepository;
import cl.bancoxyz.clientes.web.Dtos.ActualizarClienteRequest;
import cl.bancoxyz.clientes.web.Dtos.CrearClienteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteService.class);

    private final ClienteRepository clientes;
    private final NotificacionRepository notificaciones;

    public ClienteService(ClienteRepository clientes, NotificacionRepository notificaciones) {
        this.clientes = clientes;
        this.notificaciones = notificaciones;
    }

    @Transactional
    public Cliente crear(CrearClienteRequest req) {
        String rut = Rut.normalizar(req.rut());
        if (!Rut.esValido(rut)) {
            throw new ReglaNegocioException("RUT_INVALIDO", "El RUT " + req.rut() + " no es válido", HttpStatus.BAD_REQUEST);
        }
        if (clientes.existsByRut(rut)) {
            throw new ReglaNegocioException("CLIENTE_EXISTE", "Ya existe un cliente con RUT " + rut, HttpStatus.CONFLICT);
        }
        Cliente c = clientes.save(new Cliente(rut, req.nombres(), req.apellidos(), req.email(), req.telefono(),
                req.direccion(), req.fechaNacimiento(), req.segmento() == null ? Segmento.PERSONA : req.segmento()));
        log.info("Cliente {} registrado con id {}", rut, c.getId());
        return c;
    }

    @Transactional(readOnly = true)
    public Cliente obtener(Long id) {
        return clientes.findById(id).orElseThrow(() ->
                new ReglaNegocioException("CLIENTE_NO_ENCONTRADO", "No existe el cliente " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Cliente obtenerPorRut(String rut) {
        return clientes.findByRut(Rut.normalizar(rut)).orElseThrow(() ->
                new ReglaNegocioException("CLIENTE_NO_ENCONTRADO", "No existe el cliente " + rut, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Cliente> listar(int pagina, int tamanio) {
        return clientes.findAll(PageRequest.of(pagina, Math.min(tamanio, 100), Sort.by("id"))).getContent();
    }

    @Transactional
    public Cliente actualizar(Long id, ActualizarClienteRequest req) {
        Cliente c = obtener(id);
        if (req.email() != null) c.setEmail(req.email());
        if (req.telefono() != null) c.setTelefono(req.telefono());
        if (req.direccion() != null) c.setDireccion(req.direccion());
        if (req.segmento() != null) c.setSegmento(req.segmento());
        return c;
    }

    /** Baja lógica: no se borran datos personales por requisitos de auditoría. */
    @Transactional
    public Cliente desactivar(Long id) {
        Cliente c = obtener(id);
        if (c.getCuentasActivas() > 0) {
            throw new ReglaNegocioException("CLIENTE_CON_CUENTAS",
                    "El cliente tiene " + c.getCuentasActivas() + " cuenta(s) activa(s)", HttpStatus.CONFLICT);
        }
        c.setActivo(false);
        return c;
    }

    @Transactional(readOnly = true)
    public List<Notificacion> notificaciones(Long clienteId, int limite) {
        obtener(clienteId);
        return notificaciones.findByClienteIdOrderByFechaDescIdDesc(clienteId, PageRequest.of(0, Math.min(limite, 100)));
    }

    // ------------------------------------------------------------ usados por los consumidores Kafka

    /** Registra la notificación sólo si ese evento no se procesó antes (consumidor idempotente). */
    @Transactional
    public void notificar(Long clienteId, String tipo, String nivel, String mensaje, String eventoId) {
        if (clienteId == null || !clientes.existsById(clienteId)) {
            log.debug("Evento {} ignorado: cliente {} no existe", eventoId, clienteId);
            return;
        }
        if (notificaciones.existsByEventoIdAndClienteId(eventoId, clienteId)) {
            log.info("Evento {} ya procesado para cliente {}, se omite", eventoId, clienteId);
            return;
        }
        notificaciones.save(new Notificacion(clienteId, tipo, nivel, mensaje, eventoId));
    }

    @Transactional
    public void ajustarCuentasActivas(Long clienteId, int delta, String eventoId) {
        clientes.findById(clienteId).ifPresent(c -> {
            if (!notificaciones.existsByEventoIdAndClienteId(eventoId, clienteId)) {
                c.setCuentasActivas(Math.max(0, c.getCuentasActivas() + delta));
            }
        });
    }
}
