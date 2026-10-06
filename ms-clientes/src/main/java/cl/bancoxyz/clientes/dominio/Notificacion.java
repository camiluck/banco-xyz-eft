package cl.bancoxyz.clientes.dominio;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Notificación generada a partir de eventos Kafka (transacciones, alertas, cambios de cuenta). */
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, length = 10)
    private String nivel;

    @Column(nullable = false, length = 300)
    private String mensaje;

    /** topico-particion-offset: evita procesar dos veces el mismo mensaje (entrega "al menos una vez"). */
    @Column(name = "evento_id", nullable = false, length = 120)
    private String eventoId;

    @Column(nullable = false)
    private LocalDateTime fecha;

    protected Notificacion() {
    }

    public Notificacion(Long clienteId, String tipo, String nivel, String mensaje, String eventoId) {
        this.clienteId = clienteId;
        this.tipo = tipo;
        this.nivel = nivel;
        this.mensaje = mensaje;
        this.eventoId = eventoId;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public String getTipo() { return tipo; }
    public String getNivel() { return nivel; }
    public String getMensaje() { return mensaje; }
    public String getEventoId() { return eventoId; }
    public LocalDateTime getFecha() { return fecha; }
}
