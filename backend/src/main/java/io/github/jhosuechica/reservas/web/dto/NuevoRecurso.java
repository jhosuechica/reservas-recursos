package io.github.jhosuechica.reservas.web.dto;

import io.github.jhosuechica.reservas.dominio.TipoRecurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Alta de un recurso.
 *
 * <p>La coherencia entre tipo y capacidad no se valida aqui: la impone el
 * esquema, y el manejador de errores traduce la violacion a un 409. Duplicar
 * la regla en dos sitios es como se desincronizan.
 */
public record NuevoRecurso(
        @NotBlank @Size(max = 20) String codigo,
        @NotBlank @Size(max = 120) String nombre,
        @NotNull TipoRecurso tipo,
        @Positive Integer capacidad,
        @NotBlank @Size(max = 120) String ubicacion) {
}
