package cl.bancoxyz.clientes.web;

import cl.bancoxyz.clientes.dominio.Cliente;
import cl.bancoxyz.clientes.dominio.Notificacion;
import cl.bancoxyz.clientes.dominio.Segmento;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Dtos {

    private Dtos() {
    }

    public record CrearClienteRequest(
            @NotBlank String rut,
            @NotBlank @Size(max = 80) String nombres,
            @NotBlank @Size(max = 80) String apellidos,
            @NotBlank @Email String email,
            @Size(max = 20) String telefono,
            @Size(max = 200) String direccion,
            @Past LocalDate fechaNacimiento,
            Segmento segmento) {
    }

    public record ActualizarClienteRequest(
            @Email String email,
            @Size(max = 20) String telefono,
            @Size(max = 200) String direccion,
            Segmento segmento) {
    }

    public record ClienteResponse(Long id, String rut, String nombres, String apellidos, String email, String telefono,
                                  String direccion, LocalDate fechaNacimiento, Segmento segmento, boolean activo,
                                  int cuentasActivas, LocalDateTime fechaRegistro) {
        public static ClienteResponse de(Cliente c) {
            return new ClienteResponse(c.getId(), c.getRut(), c.getNombres(), c.getApellidos(), c.getEmail(),
                    c.getTelefono(), c.getDireccion(), c.getFechaNacimiento(), c.getSegmento(), c.isActivo(),
                    c.getCuentasActivas(), c.getFechaRegistro());
        }
    }

    public record NotificacionResponse(Long id, String tipo, String nivel, String mensaje, LocalDateTime fecha) {
        public static NotificacionResponse de(Notificacion n) {
            return new NotificacionResponse(n.getId(), n.getTipo(), n.getNivel(), n.getMensaje(), n.getFecha());
        }
    }
}
