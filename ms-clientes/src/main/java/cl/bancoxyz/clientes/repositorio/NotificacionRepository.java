package cl.bancoxyz.clientes.repositorio;

import cl.bancoxyz.clientes.dominio.Notificacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByClienteIdOrderByFechaDescIdDesc(Long clienteId, Pageable pageable);

    boolean existsByEventoIdAndClienteId(String eventoId, Long clienteId);
}
