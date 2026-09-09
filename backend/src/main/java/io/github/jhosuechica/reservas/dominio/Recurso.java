package io.github.jhosuechica.reservas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una sala o un equipo que se puede reservar.
 *
 * <p>La coherencia entre {@code tipo} y {@code capacidad} la garantiza una
 * restriccion CHECK del esquema, no esta clase: la capacidad es obligatoria en
 * las salas y debe estar ausente en los equipos.
 */
@Entity
@Table(name = "recursos")
public class Recurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoRecurso tipo;

    private Integer capacidad;

    @Column(nullable = false, length = 120)
    private String ubicacion;

    @Column(nullable = false)
    private boolean activo = true;

    protected Recurso() {
        // requerido por JPA
    }

    public Recurso(String codigo, String nombre, TipoRecurso tipo, Integer capacidad, String ubicacion) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.tipo = tipo;
        this.capacidad = capacidad;
        this.ubicacion = ubicacion;
        this.activo = true;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoRecurso getTipo() {
        return tipo;
    }

    public Integer getCapacidad() {
        return capacidad;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void desactivar() {
        this.activo = false;
    }
}
