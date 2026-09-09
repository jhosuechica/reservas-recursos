package io.github.jhosuechica.reservas.repositorio;

import io.github.jhosuechica.reservas.dominio.Reserva;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * Reservas que se cruzan con la ventana pedida.
     *
     * <p>La condicion es la del solapamiento de intervalos: empieza antes de
     * que la ventana acabe y acaba despues de que la ventana empiece. Se
     * escribe asi, y no comparando solo el inicio, para que una reserva que
     * arranco ayer y sigue hoy aparezca en la agenda de hoy.
     *
     * <p>El {@code join fetch} evita la consulta extra por cada reserva al
     * leer su recurso.
     */
    @Query("""
            select r from Reserva r
            join fetch r.recurso rec
            where (:recursoId is null or rec.id = :recursoId)
              and r.inicio < :hasta
              and r.fin > :desde
            order by r.inicio asc
            """)
    List<Reserva> buscarEnVentana(@Param("recursoId") Long recursoId,
                                  @Param("desde") OffsetDateTime desde,
                                  @Param("hasta") OffsetDateTime hasta);

    /**
     * Carga la reserva con su recurso ya inicializado.
     *
     * <p>La asociacion es LAZY, asi que un findById normal deja el recurso sin
     * cargar y convertirlo a DTO fuera de la transaccion falla. Traerlo aqui
     * es explicito y evita esa sorpresa.
     */
    @Query("select r from Reserva r join fetch r.recurso where r.id = :id")
    Optional<Reserva> buscarConRecurso(@Param("id") Long id);
}
