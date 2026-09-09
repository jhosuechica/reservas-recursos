package io.github.jhosuechica.reservas.servicio;

import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Convierte una violacion de restriccion del esquema en un error con sentido
 * para quien llama.
 *
 * <p>Las reglas viven en la base de datos, asi que el mensaje que ve el cliente
 * hay que construirlo aqui a partir del nombre de la restriccion. Devolver el
 * texto crudo del motor filtraria nombres de tablas y de indices.
 */
public final class TraductorDeRestricciones {

    private static final Map<String, String> MENSAJES = Map.of(
            "reservas_sin_solape",
            "Ya existe una reserva confirmada que se solapa con ese intervalo en el mismo recurso.",
            "reservas_intervalo_valido",
            "El fin de la reserva debe ser posterior a su inicio.",
            "reservas_duracion_maxima",
            "Una reserva no puede durar mas de 8 horas.",
            "recursos_codigo_unico",
            "Ya existe un recurso con ese codigo.",
            "recursos_capacidad_solo_en_salas",
            "La capacidad es obligatoria en las salas y no se admite en los equipos.",
            "recursos_tipo_valido",
            "El tipo de recurso no es valido.");

    private TraductorDeRestricciones() {
    }

    /**
     * Devuelve un {@link ConflictoDeReserva} si la restriccion es conocida, y
     * la excepcion original si no lo es: inventar un mensaje para algo que no
     * se ha previsto seria peor que dejar que salga el error generico.
     */
    public static RuntimeException traducir(DataIntegrityViolationException excepcion) {
        String restriccion = nombreDeRestriccion(excepcion);
        if (restriccion == null) {
            return excepcion;
        }
        String mensaje = MENSAJES.get(restriccion);
        if (mensaje == null) {
            return excepcion;
        }
        return new ConflictoDeReserva(restriccion, mensaje);
    }

    private static String nombreDeRestriccion(DataIntegrityViolationException excepcion) {
        if (excepcion.getCause() instanceof ConstraintViolationException violacion
                && violacion.getConstraintName() != null) {
            return violacion.getConstraintName();
        }
        // Hibernate no siempre logra extraer el nombre de una restriccion de
        // exclusion, que es justo la que mas importa aqui. En ese caso se
        // busca en el texto del motor, que si lo incluye.
        Throwable causa = excepcion.getMostSpecificCause();
        String texto = causa == null ? null : causa.getMessage();
        if (texto == null) {
            return null;
        }
        for (String conocida : MENSAJES.keySet()) {
            if (texto.contains(conocida)) {
                return conocida;
            }
        }
        return null;
    }
}
