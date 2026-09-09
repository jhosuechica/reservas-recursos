package io.github.jhosuechica.reservas.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Peticion de reserva.
 *
 * <p>Aqui solo se validan reglas de forma: que los campos vengan y quepan. Que
 * el intervalo sea coherente, que no exceda la duracion maxima y que no se
 * solape con otra reserva son reglas del esquema.
 */
public record NuevaReserva(
        @NotNull Long recursoId,
        @NotBlank @Size(max = 120) String solicitante,
        @NotBlank @Size(max = 200) String motivo,
        @NotNull OffsetDateTime inicio,
        @NotNull OffsetDateTime fin) {
}
