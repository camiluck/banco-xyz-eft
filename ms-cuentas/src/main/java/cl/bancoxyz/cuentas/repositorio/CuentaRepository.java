package cl.bancoxyz.cuentas.repositorio;

import cl.bancoxyz.cuentas.dominio.Cuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    List<Cuenta> findByClienteIdOrderByIdAsc(Long clienteId);

    /**
     * Bloqueo pesimista (SELECT ... FOR UPDATE): garantiza la consistencia del saldo
     * aunque haya varias réplicas del servicio procesando movimientos de la misma cuenta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.id = :id")
    Optional<Cuenta> buscarParaActualizar(@Param("id") Long id);
}
