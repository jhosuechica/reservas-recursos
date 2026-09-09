package io.github.jhosuechica.reservas.web.dto;

import io.github.jhosuechica.reservas.dominio.Recurso;
import io.github.jhosuechica.reservas.dominio.TipoRecurso;

public record RecursoRespuesta(
        Long id,
        String codigo,
        String nombre,
        TipoRecurso tipo,
        Integer capacidad,
        String ubicacion,
        boolean activo) {

    public static RecursoRespuesta de(Recurso recurso) {
        return new RecursoRespuesta(
                recurso.getId(),
                recurso.getCodigo(),
                recurso.getNombre(),
                recurso.getTipo(),
                recurso.getCapacidad(),
                recurso.getUbicacion(),
                recurso.isActivo());
    }
}
