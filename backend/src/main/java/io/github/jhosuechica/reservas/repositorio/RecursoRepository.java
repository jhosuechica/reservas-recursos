package io.github.jhosuechica.reservas.repositorio;

import io.github.jhosuechica.reservas.dominio.Recurso;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecursoRepository extends JpaRepository<Recurso, Long> {

    List<Recurso> findAllByOrderByCodigoAsc();

    List<Recurso> findByActivoTrueOrderByCodigoAsc();

    Optional<Recurso> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
