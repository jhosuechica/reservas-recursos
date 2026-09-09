import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { NuevaReserva, Recurso, Reserva } from './modelos';

/**
 * Acceso a la API.
 *
 * Las rutas son relativas a proposito: Nginx sirve la aplicacion y hace de
 * proxy de /api hacia el backend, asi que el navegador nunca hace una peticion
 * a otro origen y no hay CORS que configurar.
 */
@Injectable({ providedIn: 'root' })
export class ReservasService {
  private readonly http = inject(HttpClient);
  private readonly api = '/api';

  recursos(soloActivos = true): Observable<Recurso[]> {
    const params = new HttpParams().set('soloActivos', soloActivos);
    return this.http.get<Recurso[]>(`${this.api}/recursos`, { params });
  }

  agenda(recursoId: number | null, desde: Date, hasta: Date): Observable<Reserva[]> {
    let params = new HttpParams()
      .set('desde', desde.toISOString())
      .set('hasta', hasta.toISOString());
    if (recursoId !== null) {
      params = params.set('recursoId', recursoId);
    }
    return this.http.get<Reserva[]>(`${this.api}/reservas`, { params });
  }

  crear(nueva: NuevaReserva): Observable<Reserva> {
    return this.http.post<Reserva>(`${this.api}/reservas`, nueva);
  }

  cancelar(id: number): Observable<Reserva> {
    return this.http.delete<Reserva>(`${this.api}/reservas/${id}`);
  }
}
