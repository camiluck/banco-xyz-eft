package cl.bancoxyz.cuentas.repositorio;

import cl.bancoxyz.cuentas.dominio.Movimiento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    Optional<Movimiento> findByReferencia(String referencia);

    List<Movimiento> findByCuentaIdOrderByFechaDescIdDesc(Long cuentaId, Pageable pageable);
}
