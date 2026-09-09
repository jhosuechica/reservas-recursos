package io.github.jhosuechica.reservas.dominio;

/**
 * Estado de una reserva.
 *
 * <p>Solo las CONFIRMADA participan en la restriccion de exclusion del
 * esquema, asi que cancelar libera el hueco sin borrar la fila.
 */
public enum EstadoReserva {
    CONFIRMADA,
    CANCELADA
}
