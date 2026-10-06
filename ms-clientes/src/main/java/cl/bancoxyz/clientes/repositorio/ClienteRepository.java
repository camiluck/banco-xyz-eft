package cl.bancoxyz.clientes.repositorio;

import cl.bancoxyz.clientes.dominio.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByRut(String rut);

    boolean existsByRut(String rut);
}
