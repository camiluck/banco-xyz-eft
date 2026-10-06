package cl.bancoxyz.cuentas.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimiento tipo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(name = "saldo_resultante", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoResultante;

    /** Clave de idempotencia: el mismo movimiento nunca se aplica dos veces (reintentos seguros). */
    @Column(nullable = false, unique = true, length = 80)
    private String referencia;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    protected Movimiento() {
    }

    public Movimiento(Long cuentaId, TipoMovimiento tipo, BigDecimal monto, BigDecimal saldoResultante,
                      String referencia, String descripcion) {
        this.cuentaId = cuentaId;
        this.tipo = tipo;
        this.monto = monto;
        this.saldoResultante = saldoResultante;
        this.referencia = referencia;
        this.descripcion = descripcion;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getCuentaId() { return cuentaId; }
    public TipoMovimiento getTipo() { return tipo; }
    public BigDecimal getMonto() { return monto; }
    public BigDecimal getSaldoResultante() { return saldoResultante; }
    public String getReferencia() { return referencia; }
    public String getDescripcion() { return descripcion; }
    public LocalDateTime getFecha() { return fecha; }
}
