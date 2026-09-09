import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { App } from './app';
import { Recurso } from './modelos';

describe('App', () => {
  let fixture: ComponentFixture<App>;
  let componente: App;
  let http: HttpTestingController;

  const sala: Recurso = {
    id: 1,
    codigo: 'SALA-A',
    nombre: 'Sala de juntas Norte',
    tipo: 'SALA',
    capacidad: 12,
    ubicacion: 'Piso 3',
    activo: true,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(App);
    componente = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function arrancar(): void {
    fixture.detectChanges();
    http.expectOne((peticion) => peticion.url === '/api/recursos').flush([sala]);
    http.expectOne((peticion) => peticion.url === '/api/reservas').flush([]);
  }

  it('pide el catálogo y la agenda al arrancar', () => {
    arrancar();

    expect(componente.recursos().length).toBe(1);
    expect(componente.formulario.recursoId).toBe(sala.id);
  });

  it('llama a la API en rutas relativas, para que Nginx haga de proxy', () => {
    fixture.detectChanges();

    const recursos = http.expectOne((peticion) => peticion.url === '/api/recursos');
    expect(recursos.request.url.startsWith('/api')).toBeTrue();
    recursos.flush([sala]);

    http.expectOne((peticion) => peticion.url === '/api/reservas').flush([]);
  });

  it('muestra qué restricción del esquema rechazó la reserva', () => {
    arrancar();

    componente.formulario.solicitante = 'Ana Torres';
    componente.formulario.motivo = 'Comité de calidad';
    componente.crear();

    http.expectOne((peticion) => peticion.url === '/api/reservas' && peticion.method === 'POST').flush(
      {
        title: 'La operación incumple una regla del sistema',
        status: 409,
        detail: 'Ya existe una reserva confirmada que se solapa con ese intervalo.',
        restriccion: 'reservas_sin_solape',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(componente.error()?.restriccion).toBe('reservas_sin_solape');
    expect(componente.aviso()).toBeNull();
  });

  it('ante un fallo de red compone un mensaje propio en lugar de quedarse en blanco', () => {
    arrancar();

    componente.formulario.solicitante = 'Ana Torres';
    componente.formulario.motivo = 'Comité de calidad';
    componente.crear();

    http
      .expectOne((peticion) => peticion.url === '/api/reservas' && peticion.method === 'POST')
      .flush(null, { status: 0, statusText: 'Unknown Error' });

    expect(componente.error()?.title).toContain('No se pudo contactar');
  });

  it('separa las reservas confirmadas de las canceladas', () => {
    fixture.detectChanges();
    http.expectOne((peticion) => peticion.url === '/api/recursos').flush([sala]);
    http.expectOne((peticion) => peticion.url === '/api/reservas').flush([
      {
        id: 1,
        recursoId: 1,
        recursoCodigo: 'SALA-A',
        recursoNombre: 'Sala de juntas Norte',
        solicitante: 'Ana Torres',
        motivo: 'Comité',
        inicio: '2026-10-05T09:00:00Z',
        fin: '2026-10-05T10:00:00Z',
        estado: 'CONFIRMADA',
      },
      {
        id: 2,
        recursoId: 1,
        recursoCodigo: 'SALA-A',
        recursoNombre: 'Sala de juntas Norte',
        solicitante: 'Bruno Mena',
        motivo: 'Anulada',
        inicio: '2026-10-05T11:00:00Z',
        fin: '2026-10-05T12:00:00Z',
        estado: 'CANCELADA',
      },
    ]);

    expect(componente.confirmadas().length).toBe(1);
    expect(componente.canceladas().length).toBe(1);
  });
});
