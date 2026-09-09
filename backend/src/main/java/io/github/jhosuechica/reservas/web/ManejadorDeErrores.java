package io.github.jhosuechica.reservas.web;

import io.github.jhosuechica.reservas.servicio.ConflictoDeReserva;
import io.github.jhosuechica.reservas.servicio.NoEncontrado;
import io.github.jhosuechica.reservas.servicio.RecursoInactivo;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones a respuestas RFC 7807.
 *
 * <p>Ningun manejador devuelve el mensaje original del motor de base de datos:
 * ese texto incluye nombres de tablas, de columnas y de indices, que no le
 * sirven a quien llama y describen la instalacion por dentro.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(NoEncontrado.class)
    public ProblemDetail noEncontrado(NoEncontrado excepcion) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, excepcion.getMessage());
        problema.setTitle("No encontrado");
        return problema;
    }

    /**
     * Una restriccion del esquema rechazo la operacion. Se responde 409 porque
     * la peticion esta bien formada: es el estado actual del sistema el que la
     * hace imposible.
     */
    @ExceptionHandler(ConflictoDeReserva.class)
    public ProblemDetail conflicto(ConflictoDeReserva excepcion) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, excepcion.getMessage());
        problema.setTitle("La operacion incumple una regla del sistema");
        problema.setProperty("restriccion", excepcion.getRestriccion());
        return problema;
    }

    @ExceptionHandler(RecursoInactivo.class)
    public ProblemDetail recursoInactivo(RecursoInactivo excepcion) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, excepcion.getMessage());
        problema.setTitle("Recurso dado de baja");
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail peticionInvalida(MethodArgumentNotValidException excepcion) {
        Map<String, String> errores = new LinkedHashMap<>();
        excepcion.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La peticion tiene campos invalidos.");
        problema.setTitle("Peticion invalida");
        problema.setProperty("errores", errores);
        return problema;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail argumentoInvalido(IllegalArgumentException excepcion) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, excepcion.getMessage());
        problema.setTitle("Peticion invalida");
        return problema;
    }
}
