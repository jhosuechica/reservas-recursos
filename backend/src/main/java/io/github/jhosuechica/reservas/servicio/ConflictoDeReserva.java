package io.github.jhosuechica.reservas.servicio;

/**
 * Una restriccion del esquema rechazo la operacion.
 *
 * <p>Lleva el nombre de la restriccion que salto para que la respuesta pueda
 * decir cual regla se incumplio sin filtrar el mensaje crudo del motor.
 */
public class ConflictoDeReserva extends RuntimeException {

    private final String restriccion;

    public ConflictoDeReserva(String restriccion, String mensaje) {
        super(mensaje);
        this.restriccion = restriccion;
    }

    public String getRestriccion() {
        return restriccion;
    }
}
