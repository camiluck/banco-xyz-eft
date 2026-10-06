package cl.bancoxyz.pagos.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Operación de pago. Guarda en qué paso va (débito aplicado / crédito aplicado) para que,
 * si un servicio falla a mitad de camino, el proceso pueda retomarse o compensarse (patrón Saga).
 */
@Entity
@Table(name = "pagos")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPago tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado;

    @Column(name = "cuenta_origen_id")
    private Long cuentaOrigenId;

    @Column(name = "cuenta_destino_id")
    private Long cuentaDestinoId;

    @Column(name = "cliente_origen_id")
    private Long clienteOrigenId;

    @Column(name = "cliente_destino_id")
    private Long clienteDestinoId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false, length = 10)
    private String canal;

    @Column(length = 300)
    private String motivo;

    @Column(name = "debito_aplicado", nullable = false)
    private boolean debitoAplicado;

    @Column(name = "credito_aplicado", nullable = false)
    private boolean creditoAplicado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Version
    private Long version;

    protected Pago() {
    }

    public Pago(String referencia, TipoPago tipo, Long cuentaOrigenId, Long cuentaDestinoId, BigDecimal monto,
                String descripcion, String canal) {
        this.referencia = referencia;
        this.tipo = tipo;
        this.cuentaOrigenId = cuentaOrigenId;
        this.cuentaDestinoId = cuentaDestinoId;
        this.monto = monto;
        this.descripcion = descripcion;
        this.canal = canal;
        this.estado = EstadoPago.PENDIENTE;
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = this.fechaCreacion;
    }

    public boolean faltaDebito() {
        return tipo.debita() && !debitoAplicado;
    }

    public boolean faltaCredito() {
        return tipo.acredita() && !creditoAplicado;
    }

    public void registrarIntento() {
        intentos++;
        tocar();
    }

    public void marcarDebito(Long clienteId) {
        debitoAplicado = true;
        clienteOrigenId = clienteId;
        tocar();
    }

    public void marcarCredito(Long clienteId) {
        creditoAplicado = true;
        clienteDestinoId = clienteId;
        tocar();
    }

    public void completar() {
        estado = EstadoPago.COMPLETADO;
        motivo = null;
        tocar();
    }

    public void rechazar(String motivo) {
        estado = EstadoPago.RECHAZADO;
        this.motivo = motivo;
        tocar();
    }

    public void revertir(String motivo) {
        estado = EstadoPago.REVERTIDO;
        this.motivo = motivo;
        tocar();
    }

    public void anotar(String motivo) {
        this.motivo = motivo;
        tocar();
    }

    private void tocar() {
        fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getReferencia() { return referencia; }
    public TipoPago getTipo() { return tipo; }
    public EstadoPago getEstado() { return estado; }
    public Long getCuentaOrigenId() { return cuentaOrigenId; }
    public Long getCuentaDestinoId() { return cuentaDestinoId; }
    public Long getClienteOrigenId() { return clienteOrigenId; }
    public Long getClienteDestinoId() { return clienteDestinoId; }
    public BigDecimal getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }
    public String getCanal() { return canal; }
    public String getMotivo() { return motivo; }
    public boolean isDebitoAplicado() { return debitoAplicado; }
    public boolean isCreditoAplicado() { return creditoAplicado; }
    public int getIntentos() { return intentos; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
