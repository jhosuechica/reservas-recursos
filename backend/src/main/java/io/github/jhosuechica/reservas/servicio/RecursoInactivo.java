package io.github.jhosuechica.reservas.servicio;

/**
 * El recurso existe pero esta dado de baja.
 *
 * <p>Es distinto de no encontrarlo: al solicitante le sirve saber que el
 * recurso existio y ya no se presta. Se traduce a un 409.
 */
public class RecursoInactivo extends RuntimeException {

    public RecursoInactivo(String mensaje) {
        super(mensaje);
    }
}
