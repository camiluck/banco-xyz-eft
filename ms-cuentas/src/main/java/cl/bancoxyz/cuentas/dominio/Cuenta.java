package cl.bancoxyz.cuentas.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cuentas")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String numero;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCuenta tipo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuenta estado;

    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    @Column(name = "intentos_fallidos_pin", nullable = false)
    private int intentosFallidosPin;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    /** Bloqueo optimista: evita que dos réplicas sobrescriban el saldo al mismo tiempo. */
    @Version
    private Long version;

    protected Cuenta() {
    }

    public Cuenta(String numero, Long clienteId, TipoCuenta tipo, BigDecimal saldo, String pinHash) {
        this.numero = numero;
        this.clienteId = clienteId;
        this.tipo = tipo;
        this.saldo = saldo;
        this.pinHash = pinHash;
        this.estado = EstadoCuenta.ACTIVA;
        this.fechaApertura = LocalDateTime.now();
    }

    public boolean estaActiva() {
        return estado == EstadoCuenta.ACTIVA;
    }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public Long getClienteId() { return clienteId; }
    public TipoCuenta getTipo() { return tipo; }
    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }
    public EstadoCuenta getEstado() { return estado; }
    public void setEstado(EstadoCuenta estado) { this.estado = estado; }
    public String getPinHash() { return pinHash; }
    public int getIntentosFallidosPin() { return intentosFallidosPin; }
    public void setIntentosFallidosPin(int intentosFallidosPin) { this.intentosFallidosPin = intentosFallidosPin; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public Long getVersion() { return version; }
}
