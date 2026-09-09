package io.github.jhosuechica.reservas.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.jhosuechica.reservas.dominio.EstadoReserva;
import io.github.jhosuechica.reservas.dominio.Recurso;
import io.github.jhosuechica.reservas.dominio.Reserva;
import io.github.jhosuechica.reservas.dominio.TipoRecurso;
import io.github.jhosuechica.reservas.repositorio.RecursoRepository;
import io.github.jhosuechica.reservas.repositorio.ReservaRepository;
import io.github.jhosuechica.reservas.web.dto.NuevaReserva;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservas;

    @Mock
    private RecursoRepository recursos;

    @InjectMocks
    private ReservaService servicio;

    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-09-10T09:00:00Z");
    private static final OffsetDateTime FIN = OffsetDateTime.parse("2026-09-10T10:00:00Z");

    private Recurso sala() {
        return new Recurso("SALA-A", "Sala de juntas Norte", TipoRecurso.SALA, 12, "Piso 3");
    }

    private NuevaReserva peticion() {
        return new NuevaReserva(1L, "Ana Torres", "Comite de calidad", INICIO, FIN);
    }

    @Nested
    @DisplayName("Crear")
    class Crear {

        @Test
        @DisplayName("falla si el recurso no existe")
        void recursoInexistente() {
            when(recursos.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crear(peticion()))
                    .isInstanceOf(NoEncontrado.class)
                    .hasMessageContaining("1");

            verify(reservas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("falla si el recurso esta dado de baja, y lo distingue de no existir")
        void recursoInactivo() {
            Recurso recurso = sala();
            recurso.desactivar();
            when(recursos.findById(1L)).thenReturn(Optional.of(recurso));

            assertThatThrownBy(() -> servicio.crear(peticion()))
                    .isInstanceOf(RecursoInactivo.class)
                    .hasMessageContaining("SALA-A");

            verify(reservas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("no comprueba el solapamiento por su cuenta: inserta y deja hablar al esquema")
        void noConsultaAntesDeInsertar() {
            when(recursos.findById(1L)).thenReturn(Optional.of(sala()));
            when(reservas.saveAndFlush(any(Reserva.class)))
                    .thenAnswer(invocacion -> invocacion.getArgument(0));

            servicio.crear(peticion());

            // Si algun dia alguien anade una consulta previa de solapamiento,
            // esta prueba lo detecta: seria reintroducir la carrera que la
            // restriccion de exclusion elimina.
            verify(reservas, never()).buscarEnVentana(any(), any(), any());
        }

        @Test
        @DisplayName("traduce el rechazo de la restriccion de exclusion a un conflicto con mensaje propio")
        void traduceElSolapamiento() {
            when(recursos.findById(1L)).thenReturn(Optional.of(sala()));
            when(reservas.saveAndFlush(any(Reserva.class)))
                    .thenThrow(violacion("reservas_sin_solape"));

            assertThatThrownBy(() -> servicio.crear(peticion()))
                    .isInstanceOf(ConflictoDeReserva.class)
                    .hasMessageContaining("se solapa")
                    // El texto crudo del motor no puede llegar al cliente.
                    .hasMessageNotContaining("exclusion constraint");
        }

        @Test
        @DisplayName("traduce tambien el tope de duracion")
        void traduceLaDuracionMaxima() {
            when(recursos.findById(1L)).thenReturn(Optional.of(sala()));
            when(reservas.saveAndFlush(any(Reserva.class)))
                    .thenThrow(violacion("reservas_duracion_maxima"));

            assertThatThrownBy(() -> servicio.crear(peticion()))
                    .isInstanceOf(ConflictoDeReserva.class)
                    .hasMessageContaining("8 horas");
        }

        @Test
        @DisplayName("una restriccion desconocida no se disfraza de conflicto de reserva")
        void restriccionDesconocida() {
            when(recursos.findById(1L)).thenReturn(Optional.of(sala()));
            when(reservas.saveAndFlush(any(Reserva.class)))
                    .thenThrow(violacion("alguna_restriccion_futura"));

            assertThatThrownBy(() -> servicio.crear(peticion()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Cancelar")
    class Cancelar {

        @Test
        @DisplayName("cambia el estado sin borrar la fila")
        void cancela() {
            Reserva reserva = new Reserva(sala(), "Ana Torres", "Comite", INICIO, FIN);
            when(reservas.buscarConRecurso(7L)).thenReturn(Optional.of(reserva));
            when(reservas.saveAndFlush(reserva)).thenReturn(reserva);

            Reserva resultado = servicio.cancelar(7L);

            assertThat(resultado.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            verify(reservas, never()).delete(any());
        }

        @Test
        @DisplayName("cancelar una ya cancelada no vuelve a escribir")
        void esIdempotente() {
            Reserva reserva = new Reserva(sala(), "Ana Torres", "Comite", INICIO, FIN);
            reserva.cancelar();
            when(reservas.buscarConRecurso(7L)).thenReturn(Optional.of(reserva));

            Reserva resultado = servicio.cancelar(7L);

            assertThat(resultado.estaCancelada()).isTrue();
            verify(reservas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("falla si la reserva no existe")
        void noExiste() {
            when(reservas.buscarConRecurso(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.cancelar(99L))
                    .isInstanceOf(NoEncontrado.class);
        }
    }

    @Nested
    @DisplayName("Agenda")
    class Agenda {

        @Test
        @DisplayName("rechaza una ventana que termina antes de empezar")
        void ventanaInvertida() {
            assertThatThrownBy(() -> servicio.agenda(null, FIN, INICIO))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(reservas, never()).buscarEnVentana(any(), any(), any());
        }

        @Test
        @DisplayName("rechaza una ventana de duracion cero")
        void ventanaVacia() {
            assertThatThrownBy(() -> servicio.agenda(null, INICIO, INICIO))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Reproduce lo que llega desde PostgreSQL: el nombre de la restriccion
     * aparece en el texto del error, no en un campo aparte.
     */
    private static DataIntegrityViolationException violacion(String restriccion) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("ERROR: conflicting key value violates exclusion constraint \""
                        + restriccion + "\""));
    }
}
