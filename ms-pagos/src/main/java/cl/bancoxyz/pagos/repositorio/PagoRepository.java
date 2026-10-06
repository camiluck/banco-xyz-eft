package cl.bancoxyz.pagos.repositorio;

import cl.bancoxyz.pagos.dominio.EstadoPago;
import cl.bancoxyz.pagos.dominio.Pago;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    Optional<Pago> findByReferencia(String referencia);

    List<Pago> findTop50ByEstadoAndFechaActualizacionBeforeOrderByFechaCreacionAsc(EstadoPago estado, LocalDateTime antes);

    List<Pago> findByEstadoAndCuentaOrigenIdAndDebitoAplicadoFalse(EstadoPago estado, Long cuentaOrigenId);

    @Query("select p from Pago p where p.cuentaOrigenId = :cuentaId or p.cuentaDestinoId = :cuentaId order by p.fechaCreacion desc, p.id desc")
    List<Pago> deCuenta(@Param("cuentaId") Long cuentaId, Pageable pageable);
}
