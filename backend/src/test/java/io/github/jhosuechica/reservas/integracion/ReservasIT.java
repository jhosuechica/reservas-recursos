package io.github.jhosuechica.reservas.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jhosuechica.reservas.dominio.EstadoReserva;
import io.github.jhosuechica.reservas.dominio.Recurso;
import io.github.jhosuechica.reservas.dominio.Reserva;
import io.github.jhosuechica.reservas.dominio.TipoRecurso;
import io.github.jhosuechica.reservas.repositorio.RecursoRepository;
import io.github.jhosuechica.reservas.repositorio.ReservaRepository;
import io.github.jhosuechica.reservas.servicio.ConflictoDeReserva;
import io.github.jhosuechica.reservas.servicio.RecursoService;
import io.github.jhosuechica.reservas.servicio.ReservaService;
import io.github.jhosuechica.reservas.web.dto.NuevaReserva;
import io.github.jhosuechica.reservas.web.dto.NuevoRecurso;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pruebas contra un PostgreSQL real.
 *
 * <p>Son imprescindibles en este proyecto: las reglas que se comprueban aqui
 * no existen en el codigo Java. Viven en el esquema, asi que solo un motor de
 * verdad puede confirmarlas. Con una base en memoria estas pruebas pasarian
 * sin comprobar nada.
 */
@SpringBootTest
@Testcontainers
// Explicito ademas del systemProperty de Failsafe, para que la clase tambien
// funcione al ejecutarla desde el IDE.
@ActiveProfiles("test")
class ReservasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReservaService reservas;

    @Autowired
    private RecursoService recursos;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private RecursoRepository recursoRepository;

    private static final OffsetDateTime NUEVE = OffsetDateTime.parse("2026-10-05T09:00:00Z");
    private static final OffsetDateTime DIEZ = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    private static final OffsetDateTime ONCE = OffsetDateTime.parse("2026-10-05T11:00:00Z");

    private Recurso sala;

    @BeforeEach
    void prepararEsquemaLimpio() {
        reservaRepository.deleteAll();
        recursoRepository.deleteAll();
        sala = recursos.crear(new NuevoRecurso(
                "SALA-A", "Sala de juntas Norte", TipoRecurso.SALA, 12, "Piso 3"));
    }

    private NuevaReserva reservaDe(Recurso recurso, OffsetDateTime inicio, OffsetDateTime fin) {
        return new NuevaReserva(recurso.getId(), "Ana Torres", "Reunion", inicio, fin);
    }

    // ------------------------------------------------------------------
    // La regla central
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dos reservas que se solapan sobre el mismo recurso no pueden coexistir")
    void rechazaElSolapamiento() {
        reservas.crear(reservaDe(sala, NUEVE, ONCE));

        assertThatThrownBy(() -> reservas.crear(reservaDe(sala, DIEZ, ONCE)))
                .isInstanceOf(ConflictoDeReserva.class)
                .hasMessageContaining("se solapa");

        assertThat(reservaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("una reserva que termina cuando la siguiente empieza no es un solapamiento")
    void permiteReservasConsecutivas() {
        reservas.crear(reservaDe(sala, NUEVE, DIEZ));

        // El rango se construye como '[)'. Con '[]' este caso, que es
        // perfectamente legitimo al reservar salas, quedaria rechazado.
        assertThatCode(() -> reservas.crear(reservaDe(sala, DIEZ, ONCE)))
                .doesNotThrowAnyException();

        assertThat(reservaRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("el mismo horario en recursos distintos si se permite")
    void distintoRecursoNoColisiona() {
        Recurso proyector = recursos.crear(new NuevoRecurso(
                "EQ-PROY-1", "Proyector portatil", TipoRecurso.EQUIPO, null, "Almacen TI"));

        reservas.crear(reservaDe(sala, NUEVE, DIEZ));

        assertThatCode(() -> reservas.crear(reservaDe(proyector, NUEVE, DIEZ)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cancelar libera el hueco sin borrar el historico")
    void cancelarLiberaElHueco() {
        Reserva primera = reservas.crear(reservaDe(sala, NUEVE, ONCE));

        reservas.cancelar(primera.getId());

        assertThatCode(() -> reservas.crear(reservaDe(sala, NUEVE, ONCE)))
                .doesNotThrowAnyException();

        // La cancelada sigue ahi: son dos filas, no una sustituida.
        assertThat(reservaRepository.count()).isEqualTo(2);
        assertThat(reservas.porId(primera.getId()).getEstado())
                .isEqualTo(EstadoReserva.CANCELADA);
    }

    // ------------------------------------------------------------------
    // El resto de reglas del esquema
    // ------------------------------------------------------------------

    @Test
    @DisplayName("una reserva no puede terminar antes de empezar")
    void rechazaElIntervaloInvertido() {
        assertThatThrownBy(() -> reservas.crear(reservaDe(sala, ONCE, NUEVE)))
                .isInstanceOf(ConflictoDeReserva.class)
                .hasMessageContaining("posterior");
    }

    @Test
    @DisplayName("una reserva no puede durar mas de ocho horas")
    void rechazaLaDuracionExcesiva() {
        assertThatThrownBy(() -> reservas.crear(reservaDe(sala, NUEVE, NUEVE.plusHours(9))))
                .isInstanceOf(ConflictoDeReserva.class)
                .hasMessageContaining("8 horas");
    }

    @Test
    @DisplayName("ocho horas justas si se admiten: el limite es inclusivo")
    void admiteElLimiteExacto() {
        assertThatCode(() -> reservas.crear(reservaDe(sala, NUEVE, NUEVE.plusHours(8))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no se puede repetir el codigo de un recurso")
    void rechazaElCodigoDuplicado() {
        assertThatThrownBy(() -> recursos.crear(new NuevoRecurso(
                "SALA-A", "Otra sala", TipoRecurso.SALA, 4, "Piso 1")))
                .isInstanceOf(ConflictoDeReserva.class)
                .hasMessageContaining("codigo");
    }

    @Test
    @DisplayName("una sala exige capacidad y un equipo no la admite")
    void capacidadCoherenteConElTipo() {
        assertThatThrownBy(() -> recursos.crear(new NuevoRecurso(
                "SALA-Z", "Sala sin aforo", TipoRecurso.SALA, null, "Piso 1")))
                .isInstanceOf(ConflictoDeReserva.class)
                .hasMessageContaining("capacidad");

        assertThatThrownBy(() -> recursos.crear(new NuevoRecurso(
                "EQ-Z", "Equipo con aforo", TipoRecurso.EQUIPO, 5, "Almacen TI")))
                .isInstanceOf(ConflictoDeReserva.class)
                .hasMessageContaining("capacidad");
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la agenda incluye una reserva que empezo antes de la ventana y sigue dentro")
    void laAgendaMiraElSolapamientoNoSoloElInicio() {
        reservas.crear(reservaDe(sala, NUEVE, ONCE));

        List<Reserva> encontradas = reservas.agenda(sala.getId(), DIEZ, ONCE.plusHours(1));

        assertThat(encontradas)
                .as("una reserva en curso al abrir la ventana debe aparecer")
                .hasSize(1);
    }

    @Test
    @DisplayName("la agenda excluye lo que queda fuera de la ventana")
    void laAgendaFiltra() {
        reservas.crear(reservaDe(sala, NUEVE, DIEZ));

        assertThat(reservas.agenda(sala.getId(), ONCE, ONCE.plusHours(2))).isEmpty();
    }
}
