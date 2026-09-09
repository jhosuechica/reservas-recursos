package io.github.jhosuechica.reservas.web;

import io.github.jhosuechica.reservas.servicio.ReservaService;
import io.github.jhosuechica.reservas.web.dto.NuevaReserva;
import io.github.jhosuechica.reservas.web.dto.ReservaRespuesta;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservas;

    public ReservaController(ReservaService reservas) {
        this.reservas = reservas;
    }

    /**
     * Agenda de reservas que se cruzan con la ventana pedida.
     *
     * <p>Sin fechas devuelve el dia de hoy, que es lo que quiere ver quien
     * abre la aplicacion.
     */
    @GetMapping
    public List<ReservaRespuesta> agenda(
            @RequestParam(required = false) Long recursoId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {

        OffsetDateTime inicioDelDia = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toOffsetDateTime();

        OffsetDateTime desdeEfectivo = desde != null ? desde : inicioDelDia;
        OffsetDateTime hastaEfectivo = hasta != null ? hasta : desdeEfectivo.plusDays(1);

        return reservas.agenda(recursoId, desdeEfectivo, hastaEfectivo).stream()
                .map(ReservaRespuesta::de)
                .toList();
    }

    @GetMapping("/{id}")
    public ReservaRespuesta porId(@PathVariable Long id) {
        return ReservaRespuesta.de(reservas.porId(id));
    }

    @PostMapping
    public ResponseEntity<ReservaRespuesta> crear(@Valid @RequestBody NuevaReserva peticion) {
        ReservaRespuesta creada = ReservaRespuesta.de(reservas.crear(peticion));
        return ResponseEntity
                .created(URI.create("/api/reservas/" + creada.id()))
                .body(creada);
    }

    /**
     * Cancela la reserva. Responde con la reserva ya cancelada en vez de un
     * 204 vacio, para que el cliente vea el estado resultante sin volver a
     * preguntar.
     */
    @DeleteMapping("/{id}")
    public ReservaRespuesta cancelar(@PathVariable Long id) {
        return ReservaRespuesta.de(reservas.cancelar(id));
    }
}
