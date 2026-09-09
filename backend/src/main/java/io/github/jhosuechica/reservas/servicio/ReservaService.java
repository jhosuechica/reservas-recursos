package io.github.jhosuechica.reservas.servicio;

import io.github.jhosuechica.reservas.dominio.Recurso;
import io.github.jhosuechica.reservas.dominio.Reserva;
import io.github.jhosuechica.reservas.repositorio.RecursoRepository;
import io.github.jhosuechica.reservas.repositorio.ReservaRepository;
import io.github.jhosuechica.reservas.web.dto.NuevaReserva;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservaService {

    private final ReservaRepository reservas;
    private final RecursoRepository recursos;

    public ReservaService(ReservaRepository reservas, RecursoRepository recursos) {
        this.reservas = reservas;
        this.recursos = recursos;
    }

    @Transactional(readOnly = true)
    public List<Reserva> agenda(Long recursoId, OffsetDateTime desde, OffsetDateTime hasta) {
        if (!hasta.isAfter(desde)) {
            throw new IllegalArgumentException(
                    "La ventana consultada debe terminar despues de empezar.");
        }
        return reservas.buscarEnVentana(recursoId, desde, hasta);
    }

    @Transactional(readOnly = true)
    public Reserva porId(Long id) {
        return reservas.buscarConRecurso(id)
                .orElseThrow(() -> new NoEncontrado("No existe la reserva con id " + id));
    }

    /**
     * Crea una reserva confirmada.
     *
     * <p>El solapamiento no se comprueba aqui a proposito. Comprobarlo antes
     * de insertar dejaria una ventana entre la lectura y la escritura en la
     * que otra peticion puede colarse. La restriccion de exclusion no tiene
     * esa ventana, asi que este metodo intenta insertar y traduce el rechazo.
     */
    @Transactional
    public Reserva crear(NuevaReserva peticion) {
        Recurso recurso = recursos.findById(peticion.recursoId())
                .orElseThrow(() -> new NoEncontrado(
                        "No existe el recurso con id " + peticion.recursoId()));

        if (!recurso.isActivo()) {
            throw new RecursoInactivo(
                    "El recurso " + recurso.getCodigo() + " esta dado de baja y no admite reservas.");
        }

        Reserva reserva = new Reserva(
                recurso, peticion.solicitante(), peticion.motivo(), peticion.inicio(), peticion.fin());

        try {
            // saveAndFlush envia el INSERT ahora mismo. Con save() la violacion
            // saltaria al confirmar la transaccion, ya fuera de este try, y no
            // se podria traducir a una respuesta util.
            return reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException excepcion) {
            throw TraductorDeRestricciones.traducir(excepcion);
        }
    }

    /**
     * Cancela una reserva. No la borra: cambiar el estado la saca de la
     * restriccion parcial, asi que el hueco queda libre y el historico se
     * conserva.
     */
    @Transactional
    public Reserva cancelar(Long id) {
        Reserva reserva = reservas.buscarConRecurso(id)
                .orElseThrow(() -> new NoEncontrado("No existe la reserva con id " + id));

        if (reserva.estaCancelada()) {
            return reserva;
        }

        reserva.cancelar();
        return reservas.saveAndFlush(reserva);
    }
}
