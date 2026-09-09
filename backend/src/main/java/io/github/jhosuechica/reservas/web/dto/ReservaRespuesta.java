package io.github.jhosuechica.reservas.web.dto;

import io.github.jhosuechica.reservas.dominio.EstadoReserva;
import io.github.jhosuechica.reservas.dominio.Reserva;
import java.time.OffsetDateTime;

public record ReservaRespuesta(
        Long id,
        Long recursoId,
        String recursoCodigo,
        String recursoNombre,
        String solicitante,
        String motivo,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        EstadoReserva estado) {

    public static ReservaRespuesta de(Reserva reserva) {
        return new ReservaRespuesta(
                reserva.getId(),
                reserva.getRecurso().getId(),
                reserva.getRecurso().getCodigo(),
                reserva.getRecurso().getNombre(),
                reserva.getSolicitante(),
                reserva.getMotivo(),
                reserva.getInicio(),
                reserva.getFin(),
                reserva.getEstado());
    }
}
