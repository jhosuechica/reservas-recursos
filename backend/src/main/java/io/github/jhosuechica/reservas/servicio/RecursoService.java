package io.github.jhosuechica.reservas.servicio;

import io.github.jhosuechica.reservas.dominio.Recurso;
import io.github.jhosuechica.reservas.repositorio.RecursoRepository;
import io.github.jhosuechica.reservas.web.dto.NuevoRecurso;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecursoService {

    private final RecursoRepository recursos;

    public RecursoService(RecursoRepository recursos) {
        this.recursos = recursos;
    }

    @Transactional(readOnly = true)
    public List<Recurso> listar(boolean soloActivos) {
        return soloActivos
                ? recursos.findByActivoTrueOrderByCodigoAsc()
                : recursos.findAllByOrderByCodigoAsc();
    }

    @Transactional(readOnly = true)
    public Recurso porId(Long id) {
        return recursos.findById(id)
                .orElseThrow(() -> new NoEncontrado("No existe el recurso con id " + id));
    }

    /**
     * Da de alta un recurso.
     *
     * <p>Ni la unicidad del codigo ni la coherencia entre tipo y capacidad se
     * comprueban antes: las dos son restricciones del esquema y se dejan
     * fallar para traducir el rechazo. Comprobarlas aqui ademas seria la misma
     * regla escrita en dos sitios.
     */
    @Transactional
    public Recurso crear(NuevoRecurso peticion) {
        Recurso recurso = new Recurso(
                peticion.codigo(),
                peticion.nombre(),
                peticion.tipo(),
                peticion.capacidad(),
                peticion.ubicacion());
        try {
            return recursos.saveAndFlush(recurso);
        } catch (DataIntegrityViolationException excepcion) {
            throw TraductorDeRestricciones.traducir(excepcion);
        }
    }
}
