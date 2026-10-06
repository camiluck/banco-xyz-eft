package cl.bancoxyz.clientes.dominio;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String rut;

    @Column(nullable = false, length = 80)
    private String nombres;

    @Column(nullable = false, length = 80)
    private String apellidos;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 20)
    private String telefono;

    @Column(length = 200)
    private String direccion;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Segmento segmento;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "cuentas_activas", nullable = false)
    private int cuentasActivas;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Version
    private Long version;

    protected Cliente() {
    }

    public Cliente(String rut, String nombres, String apellidos, String email, String telefono, String direccion,
                   LocalDate fechaNacimiento, Segmento segmento) {
        this.rut = rut;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.email = email;
        this.telefono = telefono;
        this.direccion = direccion;
        this.fechaNacimiento = fechaNacimiento;
        this.segmento = segmento;
        this.activo = true;
        this.fechaRegistro = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getRut() { return rut; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public Segmento getSegmento() { return segmento; }
    public void setSegmento(Segmento segmento) { this.segmento = segmento; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public int getCuentasActivas() { return cuentasActivas; }
    public void setCuentasActivas(int cuentasActivas) { this.cuentasActivas = cuentasActivas; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
