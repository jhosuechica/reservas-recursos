package io.github.jhosuechica.reservas.servicio;

/** Se pidio algo por identificador y no existe. Se traduce a un 404. */
public class NoEncontrado extends RuntimeException {

    public NoEncontrado(String mensaje) {
        super(mensaje);
    }
}
