package io.github.jhosuechica.reservas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Reserva de un recurso durante un intervalo.
 *
 * <p>Que dos reservas confirmadas del mismo recurso no se solapen NO se
 * comprueba aqui ni en el servicio: lo impone la restriccion de exclusion
 * {@code reservas_sin_solape}. Ver el ADR-0001.
 */
@Entity
@Table(name = "reservas")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurso_id", nullable = false)
    private Recurso recurso;

    @Column(nullable = false, length = 120)
    private String solicitante;

    @Column(nullable = false, length = 200)
    private String motivo;

    @Column(nullable = false)
    private OffsetDateTime inicio;

    @Column(nullable = false)
    private OffsetDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoReserva estado = EstadoReserva.CONFIRMADA;

    @Column(name = "creada_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creadaEn;

    protected Reserva() {
        // requerido por JPA
    }

    public Reserva(Recurso recurso, String solicitante, String motivo,
                   OffsetDateTime inicio, OffsetDateTime fin) {
        this.recurso = recurso;
        this.solicitante = solicitante;
        this.motivo = motivo;
        this.inicio = inicio;
        this.fin = fin;
        this.estado = EstadoReserva.CONFIRMADA;
    }

    /** Marca la reserva como cancelada, lo que libera el intervalo. */
    public void cancelar() {
        this.estado = EstadoReserva.CANCELADA;
    }

    public boolean estaCancelada() {
        return this.estado == EstadoReserva.CANCELADA;
    }

    public Long getId() {
        return id;
    }

    public Recurso getRecurso() {
        return recurso;
    }

    public String getSolicitante() {
        return solicitante;
    }

    public String getMotivo() {
        return motivo;
    }

    public OffsetDateTime getInicio() {
        return inicio;
    }

    public OffsetDateTime getFin() {
        return fin;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public OffsetDateTime getCreadaEn() {
        return creadaEn;
    }
}
