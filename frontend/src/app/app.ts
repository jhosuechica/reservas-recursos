import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProblemDetail, Recurso, Reserva } from './modelos';
import { ReservasService } from './reservas.service';

@Component({
  selector: 'app-root',
  imports: [FormsModule, DatePipe],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  private readonly api = inject(ReservasService);

  readonly recursos = signal<Recurso[]>([]);
  readonly reservas = signal<Reserva[]>([]);
  readonly cargando = signal(false);

  readonly error = signal<ProblemDetail | null>(null);
  readonly aviso = signal<string | null>(null);

  readonly filtroRecurso = signal<number | null>(null);
  readonly fecha = signal(App.hoy());

  readonly confirmadas = computed(() =>
    this.reservas().filter((reserva) => reserva.estado === 'CONFIRMADA'),
  );

  readonly canceladas = computed(() =>
    this.reservas().filter((reserva) => reserva.estado === 'CANCELADA'),
  );

  formulario = {
    recursoId: null as number | null,
    solicitante: '',
    motivo: '',
    horaInicio: '09:00',
    horaFin: '10:00',
  };

  ngOnInit(): void {
    this.api.recursos(true).subscribe({
      next: (recursos) => {
        this.recursos.set(recursos);
        if (recursos.length > 0 && this.formulario.recursoId === null) {
          this.formulario.recursoId = recursos[0].id;
        }
      },
      error: (fallo: HttpErrorResponse) => this.mostrarError(fallo),
    });
    this.cargarAgenda();
  }

  cargarAgenda(): void {
    const [desde, hasta] = this.ventanaDelDia();
    this.cargando.set(true);
    this.api.agenda(this.filtroRecurso(), desde, hasta).subscribe({
      next: (reservas) => {
        this.reservas.set(reservas);
        this.cargando.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mostrarError(fallo);
      },
    });
  }

  crear(): void {
    this.limpiarMensajes();

    if (this.formulario.recursoId === null) {
      return;
    }

    const inicio = this.instante(this.formulario.horaInicio);
    const fin = this.instante(this.formulario.horaFin);

    this.api
      .crear({
        recursoId: this.formulario.recursoId,
        solicitante: this.formulario.solicitante,
        motivo: this.formulario.motivo,
        inicio: inicio.toISOString(),
        fin: fin.toISOString(),
      })
      .subscribe({
        next: (reserva) => {
          this.aviso.set(`Reserva creada en ${reserva.recursoNombre}.`);
          this.formulario.motivo = '';
          this.cargarAgenda();
        },
        error: (fallo: HttpErrorResponse) => this.mostrarError(fallo),
      });
  }

  cancelar(reserva: Reserva): void {
    this.limpiarMensajes();
    this.api.cancelar(reserva.id).subscribe({
      next: () => {
        this.aviso.set('Reserva cancelada. El intervalo vuelve a estar libre.');
        this.cargarAgenda();
      },
      error: (fallo: HttpErrorResponse) => this.mostrarError(fallo),
    });
  }

  nombreDeRecurso(id: number | null): string {
    if (id === null) {
      return 'Todos los recursos';
    }
    return this.recursos().find((recurso) => recurso.id === id)?.nombre ?? '';
  }

  erroresDeCampo(): { campo: string; mensaje: string }[] {
    const errores = this.error()?.errores;
    if (!errores) {
      return [];
    }
    return Object.entries(errores).map(([campo, mensaje]) => ({ campo, mensaje }));
  }

  limpiarMensajes(): void {
    this.error.set(null);
    this.aviso.set(null);
  }

  // ------------------------------------------------------------------

  private ventanaDelDia(): [Date, Date] {
    const desde = new Date(`${this.fecha()}T00:00:00`);
    const hasta = new Date(desde);
    hasta.setDate(hasta.getDate() + 1);
    return [desde, hasta];
  }

  /** Combina el dia seleccionado con una hora del formulario. */
  private instante(hora: string): Date {
    return new Date(`${this.fecha()}T${hora}:00`);
  }

  /**
   * El backend responde los errores en formato RFC 7807, asi que aqui basta
   * con quedarse el cuerpo. Cuando el fallo es de red no hay cuerpo que valga,
   * y entonces se compone un mensaje propio.
   */
  private mostrarError(fallo: HttpErrorResponse): void {
    const cuerpo = fallo.error as ProblemDetail | null;
    if (cuerpo && typeof cuerpo === 'object' && (cuerpo.detail || cuerpo.title)) {
      this.error.set(cuerpo);
      return;
    }
    this.error.set({
      title: 'No se pudo contactar con el servidor',
      status: fallo.status,
      detail: 'Comprueba que el backend este levantado.',
    });
  }

  private static hoy(): string {
    const ahora = new Date();
    const mes = `${ahora.getMonth() + 1}`.padStart(2, '0');
    const dia = `${ahora.getDate()}`.padStart(2, '0');
    return `${ahora.getFullYear()}-${mes}-${dia}`;
  }
}
