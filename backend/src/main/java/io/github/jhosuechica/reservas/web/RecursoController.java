package io.github.jhosuechica.reservas.web;

import io.github.jhosuechica.reservas.servicio.RecursoService;
import io.github.jhosuechica.reservas.web.dto.NuevoRecurso;
import io.github.jhosuechica.reservas.web.dto.RecursoRespuesta;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recursos")
public class RecursoController {

    private final RecursoService recursos;

    public RecursoController(RecursoService recursos) {
        this.recursos = recursos;
    }

    @GetMapping
    public List<RecursoRespuesta> listar(
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return recursos.listar(soloActivos).stream()
                .map(RecursoRespuesta::de)
                .toList();
    }

    @GetMapping("/{id}")
    public RecursoRespuesta porId(@PathVariable Long id) {
        return RecursoRespuesta.de(recursos.porId(id));
    }

    @PostMapping
    public ResponseEntity<RecursoRespuesta> crear(@Valid @RequestBody NuevoRecurso peticion) {
        RecursoRespuesta creado = RecursoRespuesta.de(recursos.crear(peticion));
        return ResponseEntity
                .created(URI.create("/api/recursos/" + creado.id()))
                .body(creado);
    }
}
