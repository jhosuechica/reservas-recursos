export type TipoRecurso = 'SALA' | 'EQUIPO';
export type EstadoReserva = 'CONFIRMADA' | 'CANCELADA';

export interface Recurso {
  id: number;
  codigo: string;
  nombre: string;
  tipo: TipoRecurso;
  capacidad: number | null;
  ubicacion: string;
  activo: boolean;
}

export interface Reserva {
  id: number;
  recursoId: number;
  recursoCodigo: string;
  recursoNombre: string;
  solicitante: string;
  motivo: string;
  inicio: string;
  fin: string;
  estado: EstadoReserva;
}

export interface NuevaReserva {
  recursoId: number;
  solicitante: string;
  motivo: string;
  inicio: string;
  fin: string;
}

/**
 * Error tal y como lo devuelve el backend en formato RFC 7807.
 *
 * `restriccion` solo viene en los 409, y nombra la regla del esquema que
 * rechazo la operacion. La interfaz la muestra a proposito: es la prueba de
 * que la regla la impuso la base de datos y no una comprobacion del codigo.
 */
export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  restriccion?: string;
  errores?: Record<string, string>;
}
