package cl.bancoxyz.clientes.web;

import cl.bancoxyz.clientes.dominio.Cliente;
import cl.bancoxyz.clientes.servicio.ClienteService;
import cl.bancoxyz.clientes.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService servicio;

    public ClienteController(ClienteService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody CrearClienteRequest req) {
        Cliente c = servicio.crear(req);
        return ResponseEntity.created(URI.create("/api/clientes/" + c.getId())).body(ClienteResponse.de(c));
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return ClienteResponse.de(servicio.obtener(id));
    }

    @GetMapping(params = "rut")
    public ClienteResponse porRut(@RequestParam String rut) {
        return ClienteResponse.de(servicio.obtenerPorRut(rut));
    }

    @GetMapping
    public List<ClienteResponse> listar(@RequestParam(defaultValue = "0") int pagina,
                                        @RequestParam(defaultValue = "20") int tamanio) {
        return servicio.listar(pagina, tamanio).stream().map(ClienteResponse::de).toList();
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarClienteRequest req) {
        return ClienteResponse.de(servicio.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    public ClienteResponse desactivar(@PathVariable Long id) {
        return ClienteResponse.de(servicio.desactivar(id));
    }

    @GetMapping("/{id}/notificaciones")
    public List<NotificacionResponse> notificaciones(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "10") int limite) {
        return servicio.notificaciones(id, limite).stream().map(NotificacionResponse::de).toList();
    }
}
